package com.example.partydice

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt
import kotlin.random.Random

// 1. 在这里加入 SensorEventListener 接口，让页面能“监听”传感器
class MainActivity : AppCompatActivity(), SensorEventListener {

    private val diceImages = intArrayOf(
        R.drawable.dice_1, R.drawable.dice_2, R.drawable.dice_3,
        R.drawable.dice_4, R.drawable.dice_5, R.drawable.dice_6
    )

    private var mediaPlayer: MediaPlayer? = null
    private var currentDiceCount = 5

    // --- 传感器相关的变量 ---
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isRolling = false // 防止在摇骰子动画期间重复触发
    private var lastShakeTime: Long = 0 // 记录上次摇晃的时间

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 2. 初始化传感器管理器
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val tvDiceCount = findViewById<TextView>(R.id.tvDiceCount)
        val btnMinus = findViewById<Button>(R.id.btnMinus)
        val btnPlus = findViewById<Button>(R.id.btnPlus)
        val btnRoll = findViewById<Button>(R.id.btnRoll)
        val gridLayoutDice = findViewById<GridLayout>(R.id.gridLayoutDice)

        btnMinus.setOnClickListener {
            if (currentDiceCount > 1) {
                currentDiceCount--
                tvDiceCount.text = currentDiceCount.toString()
            } else {
                Toast.makeText(this, "最少也要有1个骰子哦！", Toast.LENGTH_SHORT).show()
            }
        }

        btnPlus.setOnClickListener {
            if (currentDiceCount < 15) {
                currentDiceCount++
                tvDiceCount.text = currentDiceCount.toString()
            } else {
                Toast.makeText(this, "太多了放不下啦，最多15个！", Toast.LENGTH_SHORT).show()
            }
        }

        btnRoll.setOnClickListener {
            if (isRolling) return@setOnClickListener // 如果正在摇，就忽略点击
            isRolling = true // 标记状态为：正在摇晃

            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, R.raw.dice_roll)
            mediaPlayer?.start()

            gridLayoutDice.removeAllViews()
            val diceViews = mutableListOf<ImageView>()

            for (i in 0 until currentDiceCount) {
                val imageView = ImageView(this)
                val params = GridLayout.LayoutParams()
                params.width = 200
                params.height = 200
                params.setMargins(10, 10, 10, 10)
                imageView.layoutParams = params

                gridLayoutDice.addView(imageView)
                diceViews.add(imageView)
            }

            btnRoll.isEnabled = false
            btnMinus.isEnabled = false
            btnPlus.isEnabled = false
            btnRoll.text = "摇晃中..."

            object : CountDownTimer(1000, 100) {
                override fun onTick(millisUntilFinished: Long) {
                    for (view in diceViews) {
                        view.setImageResource(diceImages[Random.nextInt(6)])
                        view.rotation = Random.nextInt(-30, 30).toFloat()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(20)
                    }
                }

                override fun onFinish() {
                    for (view in diceViews) {
                        view.setImageResource(diceImages[Random.nextInt(6)])
                        view.rotation = 0f
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(100)
                    }

                    btnRoll.isEnabled = true
                    btnMinus.isEnabled = true
                    btnPlus.isEnabled = true
                    btnRoll.text = "摇一摇！"

                    isRolling = false // 动画结束，标记状态为：停止摇晃
                }
            }.start()
        }
    }

    // --- 3. 传感器核心逻辑 ---

    // 当页面显示时，开始监听传感器
    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    // 当页面退到后台时，停止监听（非常重要，不然会非常耗电！）
    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    // 当传感器检测到手机在动时，会自动调用这个方法
    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && !isRolling) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // 计算加速度，除以地球重力加速度得出 g力
            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            // 勾股定理计算综合受力大小
            val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

            // 阈值设定为 2.7：大约是用力甩一下手机的力度。如果太灵敏或太迟钝，可以改这个数字
            if (gForce > 2.7f) {
                val now = System.currentTimeMillis()
                // 设置一个 1 秒的冷却时间，防止一瞬间触发好几次
                if (now - lastShakeTime > 1000) {
                    lastShakeTime = now
                    // 模拟用户点击了“摇一摇”按钮
                    findViewById<Button>(R.id.btnRoll).performClick()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 这个方法不需要写东西，但接口要求必须保留
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}