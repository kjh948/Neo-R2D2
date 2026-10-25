cmake_minimum_required(VERSION 3.10)
project(r2d2_msgs)

# find dependencies
find_package(ament_cmake REQUIRED)
find_package(rosidl_default_generators REQUIRED)
find_package(std_msgs REQUIRED)

rosidl_generate_interfaces(${PROJECT_NAME}
  "msg/McuStatus.msg"
  "msg/ButtonEvent.msg"
  "msg/SoundRequest.msg"
  "msg/SoundFinished.msg"
  "srv/SetHeadAngle.srv"
  "srv/SetHeadDir.srv"
  "srv/SetPower.srv"
  "srv/SetMode.srv"
  "srv/SetProjector.srv"
  "srv/SetLed.srv"
  "srv/SetLcd.srv"
  "srv/PlayMcuSound.srv"
  "srv/PlaySound.srv"
  DEPENDENCIES std_msgs
)

ament_package()
