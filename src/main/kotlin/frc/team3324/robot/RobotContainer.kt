package frc.team3324.robot

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.GenericHID
import edu.wpi.first.wpilibj.Relay
import edu.wpi.first.wpilibj.XboxController
import edu.wpi.first.wpilibj.XboxController.Button
import edu.wpi.first.wpilibj.controller.RamseteController
import edu.wpi.first.wpilibj.geometry.Pose2d
import edu.wpi.first.wpilibj.geometry.Rotation2d
import edu.wpi.first.wpilibj.geometry.Translation2d
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard
import edu.wpi.first.wpilibj.trajectory.TrajectoryConfig
import edu.wpi.first.wpilibj.trajectory.TrajectoryGenerator
import edu.wpi.first.wpilibj.trajectory.constraint.DifferentialDriveVoltageConstraint
import edu.wpi.first.wpilibj2.command.Command
import edu.wpi.first.wpilibj2.command.CommandBase
import edu.wpi.first.wpilibj2.command.RamseteCommand
import edu.wpi.first.wpilibj2.command.button.JoystickButton
import frc.team3324.robot.climber.Climber
import frc.team3324.robot.climber.commands.RunClimber
import frc.team3324.robot.drivetrain.DriveTrain
import frc.team3324.robot.drivetrain.commands.teleop.Drive
import frc.team3324.robot.drivetrain.commands.teleop.GyroTurn
import frc.team3324.robot.intake.Intake
import frc.team3324.robot.intake.Pivot
import frc.team3324.robot.intake.commands.RunIntake
import frc.team3324.robot.intake.commands.RunPivot
import frc.team3324.robot.shooter.Shooter
import frc.team3324.robot.shooter.commands.RunShooter
import frc.team3324.robot.storage.Storage
import frc.team3324.robot.storage.commands.RunStorage
import frc.team3324.robot.util.Camera
import frc.team3324.robot.util.Consts
import frc.team3324.robot.util.PneumaticShift
import frc.team3324.robot.util.SwitchRelay
import io.github.oblarg.oblog.Logger
import java.util.function.BiConsumer
import java.util.function.Supplier

class RobotContainer {
    private val intake = Intake()
    private val storage = Storage()
    private val driveTrain = DriveTrain()
    private val climber = Climber()
    private val pivot = Pivot()
    private val relay = Relay(0)
    private val  shooter = Shooter()

    private val table = NetworkTableInstance.getDefault()
    private val cameraTable = table.getTable("chameleon-vision").getSubTable("USBCamera")

    private val primaryController = XboxController(0)
    private val secondaryController = XboxController(1)

    private val primaryRightX: Double
        get() = primaryController.getX(GenericHID.Hand.kLeft)
    private val primaryLeftY: Double
        get() = primaryController.getY(GenericHID.Hand.kRight)

    private val primaryTriggerRight: Double
        get() = primaryController.getTriggerAxis(GenericHID.Hand.kRight)
    private val primaryTriggerLeft: Double
        get() = primaryController.getTriggerAxis(GenericHID.Hand.kLeft)

    private val secondaryRightX: Double
        get() = secondaryController.getX(GenericHID.Hand.kLeft)
    private val secondRightY: Double
        get() = secondaryController.getY(GenericHID.Hand.kRight)
    private val secondLeftY: Double
        get() = secondaryController.getY(GenericHID.Hand.kLeft)

    private val secondTriggerRight: Double
        get() = secondaryController.getTriggerAxis(GenericHID.Hand.kRight)
    private val secondTriggerLeft: Double
        get() = secondaryController.getTriggerAxis(GenericHID.Hand.kLeft)




   init {
       Logger.configureLoggingAndConfig(this, true)
       Camera.schedule()
       driveTrain.defaultCommand = Drive(driveTrain, {primaryController.getY(GenericHID.Hand.kLeft)}, {primaryController.getX(GenericHID.Hand.kRight)})
       pivot.defaultCommand = RunPivot(pivot, -0.05)
       intake.defaultCommand = RunIntake(storage, intake, this::primaryTriggerLeft, this::primaryTriggerRight)
       storage.defaultCommand = RunStorage(storage, this::secondTriggerLeft, this::secondTriggerRight, this::secondRightY, this::secondLeftY)
       configureButtonBindings()


   }

    fun configureButtonBindings() {
        JoystickButton(primaryController, Button.kA.value).whenPressed(PneumaticShift(driveTrain.gearShifter))
        JoystickButton(primaryController, Button.kBumperLeft.value).whileHeld(RunPivot(pivot, 0.5))
        JoystickButton(primaryController, Button.kBumperRight.value).whileHeld(RunPivot(pivot, -0.5))
        JoystickButton(primaryController, Button.kX.value).whenPressed(SwitchRelay(relay))
        JoystickButton(primaryController, Button.kY.value).whenPressed(GyroTurn(
                1.0/120.0,
                Consts.DriveTrain.ksVolts/12,
                {cameraTable.getEntry("targetYaw").getDouble(0.0)},
                driveTrain::yaw,
                {input -> SmartDashboard.putNumber("Speed pog", input)}
        ))
        JoystickButton(secondaryController, Button.kX.value).whileHeld(RunShooter(shooter, 4800.0))
        JoystickButton(secondaryController, Button.kA.value).whileHeld(RunClimber(climber, 0.5))
        JoystickButton(secondaryController, Button.kB.value).whileHeld(RunClimber(climber, -0.5))

    }

    fun getAutoCommand(): Command {
        val voltageConstraint = DifferentialDriveVoltageConstraint(driveTrain.feedForward, driveTrain.driveKinematics, 7.0)
        val config = TrajectoryConfig(Consts.DriveTrain.LOW_GEAR_MAX_VELOCITY, Consts.DriveTrain.LOW_GEAR_MAX_ACCELERATION)

        val start = Pose2d(0.0,0.0, Rotation2d(0.0))
        val end = Pose2d(3.0, 0.0, Rotation2d(0.0))

        val interiorWaypoints = listOf(Translation2d(1.0, 1.0), Translation2d(2.0, -1.0))

        val trajectory = TrajectoryGenerator.generateTrajectory(
                start,
                interiorWaypoints,
                end,
                config)

        val ramseteCommand = RamseteCommand(
                trajectory,
                Supplier {driveTrain.pose},
                RamseteController(Consts.DriveTrain.kRamseteB, Consts.DriveTrain.kRamseteZeta),
                driveTrain.feedForward,
                driveTrain.driveKinematics,
                Supplier {driveTrain.wheelSpeeds},
                driveTrain.leftPIDController,
                driveTrain.rightPIDController,
                BiConsumer(driveTrain::tankDriveVolts),
                driveTrain)
        return ramseteCommand.andThen(Runnable {driveTrain.tankDriveVolts(0.0, 0.0)})
    }
}