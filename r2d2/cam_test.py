import cv2
import time

# Initialize the USB camera (0 is usually the built-in webcam, 1 or 2 for external USB cameras)
cap = cv2.VideoCapture(0)

# Optional: Force the camera backend and request specific resolution/FPS
cap.set(cv2.CAP_PROP_FRAME_WIDTH, 320)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 240)
cap.set(cv2.CAP_PROP_FPS, 30)

# Variables to calculate FPS
prev_frame_time = 0
new_frame_time = 0

print("Press 'q' to quit.")

while cap.isOpened():
    ret, frame = cap.read()
    if not ret:
        print("Error: Failed to grab frame.")
        break

    # Calculate FPS based on time differences
    new_frame_time = time.time()
    # fps = 1 / (time taken for 1 frame)
    fps = 1 / (new_frame_time - prev_frame_time)
    prev_frame_time = new_frame_time

    # Convert FPS to an integer string
    fps_text = f"FPS: {int(fps)}"

    # Draw the FPS onto the frame
    # cv2.putText(image, text, origin, font, scale, color, thickness)
    cv2.putText(frame, fps_text, (7, 40), cv2.FONT_HERSHEY_SIMPLEX, 
                1, (100, 255, 0), 2, cv2.LINE_AA)

    # Display the frame
    cv2.imshow('USB Camera FPS', frame)

    # Break loop if 'q' key is pressed
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

# Clean up resources
cap.release()
cv2.destroyAllWindows()

