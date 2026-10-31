#include "ros2_mpu6050/mpu6050.h"

#include <memory>
#include <iostream>
#include <unistd.h>

void calibration(const std::string &device, int address)
{
    auto mpu6050_dev = std::make_shared<Mpu6050>(device, address);
    const int no_of_samples = 500;

    double gyro_x_offset {0.0};
    double gyro_y_offset {0.0};
    double gyro_z_offset {0.0};
    double accel_x_offset {0.0};
    double accel_y_offset {0.0};
    double accel_z_offset {0.0};

    std::cout << "\n**** Starting calibration ****" << std::endl;
    std::cout << "Ensure that the mpu6050 board is positioned in a surface perpendicular to the direction gravitational accelleration" << std::endl;
    std::cout << "This may take a while depending upon the no of samples, please wait. . ." << std::endl;

    /* Read IMU data */
    Mpu6050::Mpu6050_AccelData_t AccelData;
    Mpu6050::Mpu6050_GyroData_t GyroData;

    /* Get no_of_samples samples and average the results */
    for(int i=0;i<no_of_samples;i++)
    {
        mpu6050_dev->Mpu6050_GetAccelData(AccelData);
        mpu6050_dev->Mpu6050_GetGyroData(GyroData);

        accel_x_offset += AccelData.Accel_X;
        accel_y_offset += AccelData.Accel_Y;
        accel_z_offset += AccelData.Accel_Z;
        gyro_x_offset += GyroData.Gyro_X;
        gyro_y_offset += GyroData.Gyro_Y;
        gyro_z_offset += GyroData.Gyro_Z;

        usleep(10000);
    }

    accel_x_offset /= no_of_samples;
    accel_y_offset /= no_of_samples;
    accel_z_offset /= no_of_samples;
    gyro_x_offset /= no_of_samples;
    gyro_y_offset /= no_of_samples;
    gyro_z_offset /= no_of_samples;

    /* Display results */
    std::cout << "\n\nIn the params.yaml file under config directory, copy the following results accordingly\n" << std::endl;
    
    std::cout << "Gyroscope Offsets: \ngyro_x_offset --> " << gyro_x_offset << "\ngyro_y_offset --> "
              << gyro_y_offset << "\ngyro_z_offset --> " << gyro_z_offset << std::endl;
    std::cout << "\nAccelerometer Offsets: \naccel_x_offset --> " << accel_x_offset << "\naccel_y_offset --> "
              << accel_y_offset << "\naccel_z_offset --> " << accel_z_offset << std::endl;

}

// Local fork: accept the I2C device path and address so calibration works on
// multi-digit Pi5 buses (/dev/i2c-13, /dev/i2c-14) and AD0-high boards (0x69).
int main(int argc, char* argv[])
{
    std::string device = (argc > 1) ? argv[1] : "/dev/i2c-1";
    int address = (argc > 2) ? std::stoi(argv[2], nullptr, 0) : 0x68;

    calibration(device, address);

    return 0;
}