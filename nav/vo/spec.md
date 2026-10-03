1. pi5에 mono rgb usb 카메라로 부터 로봇의 움직임을 추정해야 함
2. openvins와 같은 방식 혹은 AI 모델 기반 방법을 찾아야함
3. PI5에서는 imu data가 있으니 fusion 방법도 있어야 함. Mac에서는 imu는 없음
4. 로봇 모터 제어 api와 연동할 예정
5. 이후에는 LiDAR LD19를 pi5에 연결할 것이고, 로봇위치 추정 방법에 hector slam을 이용하여 SLAM/Navigation을 적용할 것임