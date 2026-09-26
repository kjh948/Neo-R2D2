# LightNav-0 pipeline analysis for a llama.cpp / GGUF port

Codebase: `/Users/apple/workspace/Neo-R2D2/nav/LightNav-0` (read-only research, 2026-09-26).
Backbone: **stock Qwen3-VL-4B-Instruct** — `Qwen3VLForConditionalGeneration`, loaded by vanilla
transformers (`src/lightnav/inference/model.py:214-232`; `AGENTS.md:63-66`). All
navigation-specific behavior lives in (a) the extended vocabulary (ordinary trained embedding
rows), (b) pre/post-processing outside the LLM, and (c) custom handling of vision embeddings.
There are no navigation heads and no custom model modules. This shape is highly favorable for
a llama.cpp port.

---

## 1. HF backend: full generation flow

Entry chain: `VLNInferenceEngine.generate_from_frames` (`inference/engine.py:283-313`)
→ sample dict (`inference/samples.py`) → `Qwen3VLDataProcessor.process_sample`
(`data_processor.py:278-494`) → either:
- backend `hf`: transformers `model.generate` on the full model (`engine.py:361-454`), or
- backend `vllm_local`: an **external ViT forward** (the vision tower extracted from the
  in-process vLLM engine, `model.py:349`) + vLLM decode over **pre-computed video embeddings**
  (`engine.py:260-279, 509-665`).

### Input format
- Always "video", never images. `sample["video_segments"]` = list of dicts
  `{video (T,C,H,W tensor), frame_indices, total_frames, pool_spatial, pool_mode}`
  (`data_processor.py:28-95`). Each video-placeholder token in the prompt consumes one
  segment, oldest→newest (`prompts.py:78-97`, `data_processor.py:98-132).
- Frames arrive as float tensors in **[-1, 1]**; the pipeline disables the video processor's
  own resize/rescale/frame-sampling/normalize (`data_processor.py:144-154, 298-303, 422-423`;
  engine asserts `do_normalize is False` when `_skip_normalize` is set).
- Frame preprocessing (`inference/frame_preprocessing.py:57-77`): HWC uint8 RGB → float/255 →
  **bilinear resize in float space** (`align_corners=False`, lines 10-25) → `t*2-1`.
  Release checkpoint `video_size = [256, 448]` (~16:9); code fallback `(224, 320)`
  (`eval_config.py:50`). Patch grid: patch_size 16, temporal_patch_size 2, spatial merge_size 2
  (`data_processor.py:135-141`, `processing.py` uses processor attrs).
  Merged vision tokens per full-resolution frame = `(H/32)*(W/32)` → 112/frame at 256×448,
  70/frame at 224×320 (`tests/conftest.py` fake processor follows this math).
- `--aspect_mode keep` picks a per-session size preserving the camera aspect at the same pixel
  budget, sides multiples of 32 and of the pre-ViT pooling factors
  (`frame_preprocessing.py:28-54`, `policies.py:53-69`; `tests/test_aspect_mode.py` shows the
  per-frame token count is intentionally invariant; 4:3 → 288×384, 1:1 → 352×352 for a
  256×448 ckpt, `docs/CONFIGURATION.md:172-176`).

### History and "Temporally Aware History Compression"
Two mechanisms, both LightNav-specific and both **outside the model weights**:
- (A) **Sliding window + per-segment pooling.** Ring buffer of `num_history_frames` frames
  (released ckpts: 64; code fallback default 16 with a loud warning, `eval_config.py:55,120-127`;
  buffer in `policies.py:106-164`). With pooling enabled and >2 frames, the window is split
  into **segment 1 = all but the newest 2 frames pooled at `pool_spatial`** and **segment 2 =
  newest 2 frames unpooled** (`samples.py:92-120`), matching the POOLED prompt variants
  ("history observations <video> and current observation <video>", `prompts.py:26-32, 46-52, 66-71`).
  Pre-ViT pooling pools patchified **pixel rows** on the merge-block grid, grid (t,h,w)→(t,h/f,w/f)
  (`processing.py:47-131`); post-ViT pooling pools ViT merger outputs **and deepstack features**
  with adaptive_avg_pool2d (ceil target), installed by monkey-patching the inner model's
  `get_video_features` (`processing.py:164-273, 591-660`; `model.py:254-264`; `engine.py:483-498`).
- (B) **SlowFast multi-tier history** (`slowfast.py`). Tier schema:
  `{age_lo, age_hi, mode: dense|burst|span|anchor, pair_stride, num_pairs, num_frames, pool_spatial}`;
  reference sets: current dense pool1 (ages 0-1), fast dense pool2 (2-17), mid burst pool2
  (18-89, pair_stride 6), long burst pool4 (90-255, pair_stride 12) or span+anchor variants
  (`slowfast.py:25-41`). Rules: absolute frame ids (timestamps = true elapsed time), oldest
  segment first, dense beats anchor, burst/span pairs touching an anchored frame dropped whole,
  odd segments padded by duplicating the newest frame (`slowfast.py:155-232`;
  `tests/test_slowfast.py` — e.g. the 5-tier reference set costs 396 vision tokens at 15 frames:
  pool1 2 + pool2 8 + pool4 2 per frame). SlowFast sessions keep the **whole episode** (not a
  ring) so the span tier can reach frame 0 (`policies.py:36-39, 114-127`).
  The README's "256K/576K/1M pixel budgets" are training-time knobs; at inference they
  materialize only as the recorded `video_size` + `pool_*` / `slowfast_tiers` values.
- ViT tubelet LRU cache keyed `(f0_abs, f1_abs, grid_h, grid_w)` per session; a hit returns the
  verbatim embedding, so the cache affects speed only (`vit_cache.py:1-10, 34-75`; engine caps:
  `max(32, 2*num_history_frames)`, 512 with SlowFast, `engine.py:87-102`; selective patchify
  processes only cache-miss tubelets, `processing.py:314-361`, `data_processor.py:341-397`).

### Timestamps inside the prompt
Per temporal patch (2 frames) a `"<X.X seconds>"` text prefix wraps each frame block,
computed as average of the two endpoint frames' times (`processing.py:285-312, 552-575`).
Default absolute: `t = idx / fps`. `VLN_TIMESTAMP_RELATIVE=1` (bridged from checkpoint
`timestamp_relative` at `model.py:148-153`) switches to time-before-current relative to the
**global newest frame across all segments** (`processing.py:292-312, 526-537`). Non-SlowFast
windowed samples keep the processor's default dense in-window indices for timestamps; only
SlowFast passes absolute indices (`data_processor.py:329-340, 382-397`).

### Prompt templates
Byte-exact trained contracts (`prompts.py:15-90`): VLN family ("Imagine you are a robot
programmed for navigation tasks…"), tracking family ("You are a mobile robot performing a
person-tracking task…"), and the unified template ("You are a mobile robot. You are given
visual observations over time, ordered from earliest to most recent: {videos}…" with one
video placeholder per segment, `prompts.py:93-97`). RVQ checkpoints swap the final output
sentence to "…sequence of coarse-to-fine trajectory tokens." via `to_rvq_prompt` when
`action_tokenizer.method == "rvq"` (raises on mismatch) (`prompts.py:100-126`, wired at
`model.py:173` and `samples.py:50-51, 92-111`). The chat template is the standard Qwen one;
the generation header appended for inference is the literal assistant-turn opener tokens
(`data_processor.py:478-486`).

### Generation config and output parsing
- **Greedy by default.** Env knobs `VLN_EVAL_TEMPERATURE` (0.0 = greedy), `VLN_EVAL_TOP_P`,
  `VLN_EVAL_TOP_K`, `VLN_EVAL_TRAJ_TOP1` (`engine.py:27-41`). HF path: `do_sample` only if
  temperature>0, `min_new_tokens=1`, `eos_token_id` = tokenizer eos
  (`engine.py:407-438`). vLLM path: `SamplingParams(temperature=0.0, top_p=1.0, max_tokens)`
  (`engine.py:638-656`).
- **Max new tokens**: `InferenceConfig.max_new_tokens=64` default (`inference/config.py:20`);
  server lower bound 8, auto-raised to the "decode budget" = grounding-prefix tokens (probe:
  2 if `<apos_0>` present, +1 if `<tpos_0>` present) + one action token per RVQ level (1 for
  flat) (`ws_server.py:570-573`, `serving/token_budget.py:6-60`; docs/CONFIGURATION.md:131-141).
- Optional **traj_top1 allowlist** constrains decoding to `{traj, tpos, act_l*, apos, opos,
  posxy, eos}` token ids probed from the checkpoint tokenizer (`engine.py:109-182`; applied as
  an additive -inf logits mask on HF at `engine.py:423-435` and as vLLM
  `allowed_token_ids` at `engine.py:653-654`).
- The HF engine **drops the precomputed 3D position_ids** so stock transformers recomputes
  mrope from grids (`engine.py:370-376`), and trims the sequence at the first non-ignored
  label to end exactly at the assistant answer boundary (`engine.py:315-359`,
  `data_processor.py:255-276` — labels = assistant span only, everything else -100).
- **Output**: generated ids decoded to text with special tokens kept (`skip_special_tokens=False`,
  `engine.py:443-447`); waypoints recovered by regex on that text (§3). Trained output
  layouts (per tests and code): flat tracking v2 = tpos + traj; dual-pointing = apos + opos +
  act levels; posxy = channel markers + axis pairs or sentinels
  (`tests/test_policies.py` example text: one tpos token followed by three act level tokens).

---

## 2. Vocabulary extension

All added tokens are **ordinary vocabulary rows**: trained into `model.embed_tokens` and
`lm_head` and saved in the checkpoint `model*.safetensors` — the checkpoint "exports the
stock Qwen3-VL architecture … stock transformers loads them directly" (`model.py:214-217`;
`tests/test_ckpt_generation_compat.py` asserts the architecture string is stock and there is
no custom `auto_map`; `AGENTS.md:63-66` describes the dir layout). There is **no sidecar
embedding file**; the LM head decodes them like any token, and all semantics are in the text
regexes. Names/formats/counts (`src/lightnav/vln_utils.py`):

| Family | Format | Count | Semantics | Ref |
|---|---|---|---|---|
| Flat trajectory | `<traj_{k}>` | K = 256 (default), id 0 = stop | id indexes `(K,H,3)` centroid table | `vln_utils.py:13-34` |
| RVQ action | `<act_l{level}_{code}>` | 3 levels × 256 = 768 | coarse→fine residual codes | `vln_utils.py:36-57` |
| Target position | `<tpos_{id}>` | 106 = 1 invisible + 15 az × 7 dist | az ±60° in 8° bins; dist edges (0,1,1.4,1.7,2,2.5,3.5,5) m, clamped | `vln_utils.py:60-167` |
| Affordance point (grid) | `<apos_{id}>` | 1300 = 0 none + 1..1296 (48×27 grid) + 1297 rotL/1298 rotR/1299 stop | id → cell-center pixel of frame-relative 48×27 grid | `vln_utils.py:170-212` |
| Object point (grid) | `<opos_{id}>` | 1297 = 0 not visible + 1..1296 | same grid | `vln_utils.py:178-185` |
| posxy axis tokens | `<pos_{i}>` i 0..999 | 1000 shared bins + markers `<apos>`/`<opos>` + sentinels `<rotl> <rotr> <stop> <novis>` | point = marker + two axis bins (x then y) | `vln_utils.py:215-324` |

- Pointing grid is 48×27 = 1296 cells (10 px cells at 480×270 label resolution,
  `protocol.py:141-146`); encoders clamp out-of-frame points to edge cells (`vln_utils.py:198-212, 318-350`).
- The traj_top1 allowlist probes the tokenizer (`convert_tokens_to_ids` until unk, up to
  65536 ids per family; RVQ unions all levels) rather than hardcoding sizes
  (`engine.py:109-182`).
- Tracking v2 emits tpos BEFORE traj; dual-pointing emits apos+opos BEFORE act levels
  (docstrings `engine.py:113-121`, `protocol.py:52-60`).
- **Port note:** for GGUF this only requires the added tokens present in the tokenizer
  metadata at the correct ids and the resized embedding/LM-head matrices, which convert
  1:1 from safetensors. No architecture change.

---

## 3. RVQ decoding: 3 tokens → 10 SE(2) waypoints

Bundle layout (`src/lightnav/traj_vocab.py:1-21` docstring; loader `:119-170`):
```
action_tokenizer/
  manifest.json          # method:"rvq", horizon:10, levels:[256,256,256],
                         # feature_dim:30 (=3*H), representation:"se2_diff"|"ego_abs",
                         # codebook_files, jacobian_weights_file, alpha_file, objective,
                         # encode, feature_space, stop:{l0:<int>}
  codebook_l0.npy ...    # each (levels[i], feature_dim) float32
  jacobian_weights.npy   # (feature_dim,) float32 per-dim weights
  alpha_per_source.json
```
Decode path (`traj_vocab.py:102-116` + `tracking.py:102-146`):
1. Regex-parse `<act_l(\d+)_(\d+)>` occurrences ordered by level; missing level → ValueError
   (`vln_utils.py:46-57`).
2. `recon = sum_i codebook[i][code_i]` (float32); `feat = recon / jacobian_weights`
   reshaped to `(H, 3)`.
3. `se2_diff`: rows are per-step relative transforms; integrate with
   `compose_to_abs` (`traj_vocab.py:38-72`): `x += cos(th)dx - sin(th)dy`,
   `y += sin(th)dx + cos(th)dy`, `th = wrap_to_pi(th + dth)` in float64 → absolute ego
   `(H,3)`, `x=forward, y=lateral(+left), th=yaw(+ccw)`. `ego_abs`: rows used directly.
4. Stop: explicit `code_0 == manifest.stop.l0` → zero `(H,3)` (`traj_vocab.py:93-100`), or
   all-close-to-zero decode (atol 5e-3, `tracking.py:36-39, 136-141`).
5. README resolutions: coarse 256-entry codebook ≈ 0.9 m, residuals ≈ 7 cm and 4 cm
   (README §RVQ Action Tokenizer). Any non-empty prefix decodes to an executable coarse
   trajectory.

Flat alternative: single `<traj_k>` → `centroids_whole_chunk_K{K}_h{H}.npy` lookup, shape
`(256, 10, 3)` (`tracking.py:42-57, 143-146`); centroid 0 is exactly the stop tuple
(`velocity.py:59-66`). Resolution of which decoder to use: `resolve_action_decoder_from_config`
(`tracking.py:185-238`) — eval_config `action_tokenizer.bundle_path` / `traj_vocab_path` per
task, then sibling `action_tokenizer/<task>`, `action_tokenizer/`, `traj_vocab/`
(`tests/test_decoder_resolution.py` covers each branch). Pure numpy — trivially portable.

---

## 4. eval_config.json / processor_config.json

**eval_config.json** (schema `eval_config.py:7-39`, whitelist `INFERENCE_FALLBACK_DEFAULTS`
`:54-71`; found by walking up 4 parents `:132-146`; priority caller > config > defaults
`:81-129`):
- `common`: `video_size [H,W]` (release ckpt 256×448; README examples 224×320 default),
  `max_seq_len` (8192), `pool_enable`, `pool_spatial`, `pool_mode avg|max`,
  `pool_stage pre_vit|post_vit`, `native_resolution` (must be false — inference refuses
  native-resolution ckpts, `model.py:108-116`).
- `tasks.<trackvla|vlnce>`: `num_history_frames` (e.g. 64), `predict_horizon` (10),
  `video_fps` (4), `traj_vocab_path` + `traj_vocab_K`, `action_tokenizer {method: flat|rvq,
  bundle_path}` (relative paths resolve against the config's dir, `eval_config.py:158-175`),
  `slowfast_tiers` (list or null), `prompt_style` ("unified_traj"), `timestamp_relative` (bool).
- Missing `num_history_frames` falls back to 16 with an explicit accuracy warning
  (`eval_config.py:118-127`). Task key mapping: serve `tracking`→`trackvla`, `vln`→`vlnce`
  (`ws_server.py:178-184`); the engine is built from the same entry the decoder came from
  (`tracking.py:308-324`).

**processor_config.json** is the **stock Qwen3VLProcessor config** — consumed only via
`VLNQwen3VLProcessor.from_pretrained(processor_dir, padding_side="left")`
(`model.py:243-246, 354-357`); it carries image/video processor `size` (its `longest_edge` is
read as max_pixels logging, `data_processor.py:158-161`), `patch_size=16`, `merge_size=2`,
`temporal_patch_size=2`, normalization stats (unused at inference since normalize/rescale are
disabled). LightNav adds no fields to it; all LightNav knobs are in eval_config.json. A
checkpoint dir also holds `config.json` (stock architecture + `video_token_id`/`image_token_id`,
read by the vLLM patch, `vllm_utils.py:92-94`), `tokenizer*`, `eval_config.json`, and an
`action_tokenizer/` bundle (`AGENTS.md:63-66`).

---

## 5. Serving layer (serving/ + docs/PROTOCOL.md)

Wire protocol = JSON over WebSocket, one response per request, 64 MiB max frame
(`ws_server.py:614`, `docs/PROTOCOL.md:1-30`):
```
{"action":"login","data":{"clientId"?}}        -> {rc:0,msg:"ok"}
{"action":"reset","data":{}}                   -> {rc:0,msg:"ok"}
{"action":"next","data":{"seq":int,"image":"<b64 JPEG|PNG>","instruction":str|null}}
  -> {rc:0, seq, actions:{step, actions:[[fwd_m,lat_m,yaw_rad] x H]}, stop, visible,
      latency_ms, timings_ms, raw_text(<=256 chars), pointing?{...}}
  -> if instruction empty: frame buffered only, {rc:0, seq, msg:"image received"}
```
- Image is the camera's native resolution; the server decodes to HWC uint8 RGB and resizes
  internally (`ws_server.py:88-102`, `AGENTS.md:118-119`). The instruction rides every `next`
  and can change mid-episode (`docs/DEPLOYMENT.md:66-72`).
- **Session state**: one connection = one `TrackingAgent` session (a `NavigationPolicy` frame
  ring/episode buffer + per-session `VitTubeletCache`), created lazily, nothing persists
  across connections, `reset` clears buffer+ids+cache (`ws_server.py:1-12, 269-287`,
  `policies.py:82-94`, `docs/PROTOCOL.md:12-21`).
- `stop` = decoded action all-zero; `visible` from tpos/opos/posxy decoding; `pointing`
  carries pixel coords in the **client's** frame size with `*_state` (point/rot_left/
  rot_right/stop/not_visible/none) and `*_clamped` flags (`protocol.py:38-212`).
- **Micro-batching**: one shared engine; `MicroBatchScheduler` collects up to
  `max_batch_size=8` (`MAX_BATCH_SIZE`) requests, waits at most `max_wait_ms=8` (lone
  requests flushed after `fast_flush_ms=2`), runs `_infer_batch` serially in one executor
  thread (never concurrent engine calls) (`batcher.py:31-141`, `ws_server.py:737-748`).
  `_infer_batch`: per-session sample build → per-session ViT through its tubelet cache →
  **one batched `llm_generate_batch`** → per-session numpy decode with error isolation
  (`tracking_service.py:113-231`). HF backend runs `generate` per session serially instead
  (`tracking_service.py:126-147`).
- Warmup: one synthetic prediction before the port binds (`ws_server.py:519-542`);
  ready-file supported. Decode cap auto-raised to the token budget (§1).
- Errors: rc 400 malformed / rc 500 decode failure (e.g. missing RVQ level); connection stays
  open; responses never sent twice (`docs/PROTOCOL.md:128-145`, `ws_server.py:451-475`).

---

## 6. robot_deploy/ and mujoco_demo/ clients

(ROS 2 stack in `robot_deploy/src/...`, demo in `mujoco_demo/vln_mujoco/`; both reuse the
protocol of §5 and the same MPC.)

- **Client loop**: login once, reset per episode, then every control tick capture an RGB
  frame, JPEG-encode, send `next` with incrementing seq and the instruction; on the reply
  execute the **first waypoint** for one control period — the model replans every frame,
  the remaining rows are look-ahead (`docs/DEPLOYMENT.md:63-79`). Reference clients send at
  the checkpoint's `video_fps` (4 Hz typically) or faster (`docs/DEPLOYMENT.md:75-80`).
- **Control rates** (subagent findings): robot-side main loop runs at **10 Hz** control;
  waypoints (10 rows ≈ 0.2 s spacing at the 4 Hz capture cadence) are consumed by an **MPC
  waypoint tracker** (OSQP-based linearized unicycle MPC over the received path, replanned
  each time a new chunk arrives; MPC internal sim dt 5 ms) rather than by direct first-row
  mapping; on staleness/errors the last command is reused (EVT-Bench client) or zero
  (`evt_bench/trackvla_client_agent.py:216-225`, `habitat/policy.py` docstring).
- **Velocity mapping** (two documented conventions):
  - Normalized clip form (reference clients / EVT-Bench):
    `vx=clip(fwd/0.375)`, `vy=clip(lat/0.25)`, `vyaw=clip(yaw/(pi/20))`
    (`cli/ws_client.py:56-60,162-165`; `evt_bench/trackvla_client_agent.py:44-46,106-111`;
    `docs/PROTOCOL.md:160-164`). The trajectory vocabularies cap one step at ≈0.25 m / 30°
    (`docs/DEPLOYMENT.md:82`).
  - Time-base form (AGENTS.md:120-121): rows carry **no time base**; command
    `v = forward_m / dt`, `w = yaw_rad / dt` with your control period. Habitat-style
    implementation: `velocity.py:19-56` (`v = fwd/dt m/s`, `w = deg(yaw)/dt deg/s`,
    inverse-normalized into [-1,1] and clipped to `lin_vel_range`/`ang_vel_range`; Habitat
    eval uses dt=0.25, ranges (0,2.5) and (±30) — `cli/predict.py:216-220`).
    `select_action_waypoint` skips leading zero rows before commanding
    (`habitat/policy.py:41-49`).
- **Cameras**: real-robot ROS 2 drivers publish JPEG-compressed camera topics; the client
  forwards the native frame (no client resize; server resizes)
  (`robot_deploy/src/.../camera + client nodes`, `AGENTS.md:118-119`). The MuJoCo demo
  renders and streams a **480×270** head camera as quality-90 JPEG at 4 Hz
  (`mujoco_demo/vln_mujoco/...`).
- **Embodiment adapters** (`robot_deploy`): Unitree Go2 gets standard `cmd_vel`
  (twist) commands; the LimX TRON1 adapter maps the same MPC output through its own
  locomulation interface (velocity targets scaled to the TRON1 walking policy); both sit
  behind the shared MPC so LightNav waypoints are embodiment-agnostic (README §Real-Robot
  Deployment, `robot_deploy/README.md`). MicroDuck biped in the demo reuses the same MPC,
  turning waypoints into gait commands for the bundled ONNX walking policy (README:244-249).
- **Onboard precedent**: measured on Jetson AGX Thor (aarch64): bf16 268 ms/step (3.7 Hz),
  `fp8_llm_only` 178 ms/step (5.6 Hz), closed loop camera→client→server→MPC 4.5-4.8 Hz with
  640×360 input, 64-frame SlowFast history (`docs/JETSON_THOR.md:34-48, 115-125`).
  Full-model fp8 was rejected: quantizing the ViT flipped 91/348 visibility flags and
  121 stop/go decisions and slowed the ViT ~2× (`vllm_utils.py:258-268`).

---

## 7. The vLLM patch (inference/vllm_utils.py) — non-standard behaviors to replicate

vLLM is pinned exactly to 0.19.1 / transformers 5.8.0 with a runtime version guard
(`vllm_utils.py:14-48`, `pyproject.toml` vllm extra). Two private-API bindings:

1. **`BaseRenderer._process_multimodal`** (`vllm_utils.py:51-166`): lets a prompt carry
   `multi_modal_data = {"video": {"video_embeds": <CPU tensor>, "video_grid_thw": ...}}` —
   i.e. **pre-computed ViT embeddings (base + deepstack concatenated along hidden dim)**
   instead of pixels (`model.py:316-320`, `engine.py:44-56`; embeds must be CPU tensors —
   vLLM pins host memory for the async H2D copy, `engine.py:45-48`, staged through pinned
   per-slot buffers `engine.py:192-215`). The patch scans `prompt_token_ids` for runs of
   `video_token_id` (id read from the HF config), packs them into `PlaceholderRange`s sized
   by `grid/(merge^2)` per video, and builds `MultiModalKwargsItems` with an `is_embed` mask
   when timestamp text tokens split the runs (the per-frame `"<X.X seconds>"` interleaving).
2. **`Qwen3VLForConditionalGeneration.get_mrope_input_positions`**
   (`vllm_utils.py:168-205`): vLLM's stock mrope computes positions by scanning for one
   vision-start token **per video frame**; LightNav prompts (the training convention) wrap
   each video with a **single** vision-start/end block with timestamp text between frame
   blocks, so the patch replaces it with HF's `Qwen3VLModel.get_rope_index` driven by
   `mm_token_type_ids` (image=1/video=2) and the grids, returning
   `(mrope_positions [3,seq], mrope_position_delta)`. The same HF `get_rope_index` closure is
   used for the position-id function in both backends (`model.py:270-312`,
   `data_processor.py:443-456`).

Engine kwargs also encode assumptions (`vllm_utils.py:351-412`): `enable_mm_embeds=True`,
`limit_mm_per_prompt={"video": 2}` (max two segments in the windowed-pooled layout),
`max_model_len = max(2048, num_frames*24 + 1024)` (vision tokens ≈ 12-20/frame pooled + text),
explicit KV-cache bytes skipping vLLM's profile run, bf16, in-process model access to extract
`visual` (`VLLM_ENABLE_V1_MULTIPROCESSING=0`, `model.py:321-350`, `vllm_utils.py:208-235`).
The `fp8_llm_only` patch (`vllm_utils.py:254-328`) keeps every `visual.*` linear bf16 while
the LLM linears go fp8 — on-the-fly quantization of bf16 safetensors
(`tests/test_fp8_quant.py`); full-model fp8 is refused.

---

## 8. Assessment: standard Qwen3-VL vs LightNav-specific; llama.cpp feasibility

### Standard Qwen3-VL behavior (llama.cpp already handles)
- Model architecture and weights: stock `Qwen3VLForConditionalGeneration` — ViT
  (patch 16 / temporal 2 / merge 2 + deepstack multi-layer vision injection), 3-D mrope,
  chat template. llama.cpp has upstream Qwen3-VL support including mmproj + deepstack.
- `"<X.X seconds>"` per-tubelet timestamp prompt interleaving is native Qwen3-VL behavior
  (the code subclasses the stock processor and keeps the render byte-identical,
  `processing.py:285-312`).
- Vocab extension by added tokens with trained embedding rows is a standard fine-tune
  pattern; GGUF carries it via tokenizer metadata + resized tensors.
- Greedy decoding of 3-4 tokens after a long multimodal prefill.

### LightNav-specific additions (must be replicated, but all outside the transformer math)
1. **~4,700 added action/pointing tokens** (§2 table) and the exact text regex decoders.
2. **Prompt templates** byte-exact (§1) and the RVQ output-sentence swap.
3. **History compression**: per-segment pre- or post-ViT spatial pooling of vision tokens
   (grid shrink before merger vs pooled embeddings after), and the SlowFast tier sampler with
   absolute-id timestamps + relative-timestamp mode. These change the **vision token count,
   grids, and mrope positions** the LLM sees — they must be reproduced bit-for-bit or
   accuracy silently degrades (`eval_config.py:108-116, 118-127`).
4. **Pre-normalized frames**: resize in float, scale to [-1,1], **no mean/std normalization**
   — llama.cpp's stock Qwen-VL preprocessing applies mean/std, so preprocessing must be
   bypassed/custom.
5. **External pre-computed ViT embeddings** + per-session tubelet cache (the whole
   vllm_local design). llama.cpp's public API cannot inject per-tubelet embeddings with
   custom grids; you either (a) fork the mtmd/llm-build path to accept external embeds
   (closest to the existing design, enables the cache), (b) let llama.cpp's mmproj run each
   segment at its pooled grid — impossible for pre-ViT pooling (pooling happens on pixels
   before the ViT, changing grid) and workable only for post-ViT style if implemented
   there, or (c) drop the cache (recompute the ViT every step — cost is the encoder, not the
   LLM; on Thor misses are ~1 tubelet/step and cached steps measurably faster, so this is a
   performance regression, not a correctness one, `JETSON_THOR.md:79-97`).
6. **mrope positions with the single-vision-start-per-video layout** — the vLLM patch exists
   precisely because stock engines' per-frame assumption breaks here; the llama.cpp port must
   compute positions the same way (HF `get_rope_index` semantics).
7. **Post-processing**: RVQ/centroid → SE(2) integration, stop detection, pointing payload
   — pure numpy, trivially portable, no GGUF involvement.
8. **fp8-LLM-only / bf16-ViT** is a vLLM-specific quantization scheme; the llama.cpp analog
   is a quantized LLM GGUF (Q4/Q5) plus a **full-precision (f16) mmproj** — the code's own
   evidence says quantizing the ViT corrupts perception, and llama.cpp mmprojs ship f16 by
   default, so this maps naturally. Watch instead for quantization of the **added-token rows
   of lm_head/embeddings** (Q4 quantization can blur near-neighbour action codes; the RVQ
   residual levels decode at 4-7 cm, so evaluate stop/waypoint agreement, mirroring their
   fp8 acceptance test: 97.4% stop agreement, p50 4.4 cm displacement).

### Sizing for the targets
- Per-step sequence at the released config (256×448, 64-frame SlowFast, pools 1/2/4):
  roughly 100-200 merged tokens/frame-tier-mix ≈ **well under 2k vision tokens** +
  timestamps/text + 3-4 generated tokens; engine sizing uses ~24 tokens/frame
  (`vllm_utils.py:386-390`), so an 4-8k context llama.cpp instance is ample.
- 4B model ≈ 8-9 GB bf16 → in Q4_K_M (~2.5 GB) + f16 mmproj (~0.5-1 GB) + short KV,
  both Apple Silicon 8-16 GB and Raspberry Pi 5 8 GB can *hold* the model. Throughput is
  the open question: every step is a fresh multimodal prefill (image cache helps only if
  you replicate the tubelet-cache idea); the Jetson-Thor datapoint (4B, GPU, 3.7-5.6 Hz
  server-side, ~2-3k token prefill) implies Pi5 CPU/Metal will land around ≤1 Hz and
  Apple Silicon MPS/Metal (with llama.cpp) several Hz. Plan for reduced history tiers or
  a smaller `num_history_frames` on Pi5, and validate greedy waypoint agreement after
  quantization — the pipeline's own tolerance studies (fp8 replay) show how they expect
  such a change to be gated.

### Ground truth in tests (all CPU-only, no weights needed — `make test`)
- `tests/conftest.py`: fake processor mirrors patch16/tp2/merge2 and token math.
- `test_traj_vocab.py`: token round-trips, bin clamping, RVQ bundle validation,
  `se2_diff` composition, stop-l0 semantics.
- `test_prompt_dict.py` (31 lines): the vLLM prompt entry shape (`prompt_token_ids` +
  `multi_modal_data.video.{video_embeds,video_grid_thw}`, embeds must be CPU).
- `test_fp8_quant.py`: the `visual.` prefix guard of the quant patch + `_assert_visual_kept_bf16`.
- `test_decoder_resolution.py`: every decoder-resolution branch of `resolve_action_decoder_from_config`.
- `test_slowfast.py`: tier layouts, pad-to-even, anchor precedence, token budgets.
- `test_vit_cache.py`: tubelet key format, hit/miss, reassembly order.
- `test_frame_preprocessing.py`: [-1,1] contract, bilinear-in-float, shape enforcement.
- `test_policies.py`: output layouts like one tpos + three act tokens; decode error isolation.
- `test_velocity.py` / `test_tracking_decode.py` / `test_tracking_service.py` /
  `test_batcher.py`: the velocity formulas, protocol decode, batched service, scheduler timing.
- `test_aspect_mode.py`: per-session size selection keeps token counts invariant.
- `test_ckpt_generation_compat.py`: stock architecture + generation-config compatibility.

### Bottom line
The model itself is a stock Qwen3-VL with ~4.7k added vocabulary tokens whose embeddings are
trained into the checkpoint — GGUF conversion is the easy part. The genuinely LightNav-specific
engineering a llama.cpp port must replicate is: (1) exact prompt template + timestamp/relative-
timestamp rendering and the resulting mrope positions; (2) the two history-compression modes
per-segment pooling and SlowFast sampling, which dictate per-segment grids; (3) the
[-1,1]-only preprocessing and (ideally) external per-tubelet ViT-embedding injection — the
current design feeds the LLM *precomputed embeddings*, which llama.cpp does not expose as an
API and which is the single biggest implementation delta; (4) all of the RVQ/pointing decode,
which is portable numpy. A pragmatic first milestone is: reuse the existing Python
`data_processor`/`slowfast`/`vit_cache` front-end unchanged, run the ViT in PyTorch
(Metal/CPU), and call a patched llama.cpp that accepts `mm_embeddings` with per-segment
grids (mirroring `_process_multimodal`'s placeholder logic), keeping everything else of
`serving/` intact.
