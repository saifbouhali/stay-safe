package com.example.wedrive

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.wedrive.ml.DrivingBehaviorCnn
import com.example.wedrive.ui.theme.WeDriveTheme
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.util.Timer
import java.util.TimerTask
class MainActivity : ComponentActivity() {
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var sensorManager: SensorManager
    private var gyroscopeSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private lateinit var accTextView: TextView
    private lateinit var gyroValuesTextView: TextView
    private lateinit var predictionValuesTextView: TextView
    private lateinit var pieChart: PieChart

    private lateinit var handler: Handler
    private val timer = Timer()
    private lateinit var model: DrivingBehaviorCnn
    private  var accX : Float = 0f
    private var accY : Float = 0f
    private var accZ : Float = 0f
    private var gyroX : Float = 0f
    private var gyroY : Float = 0f
    private var gyroZ : Float = 0f
    private val allPredictions = IntArray(10) { 0 }
    private var currentIndex = 0
    private lateinit var mediaPlayer: MediaPlayer
    private val saveDataRunnable = Runnable {
        predictingBehavior()
    }


    private val accelerometerEventListener = object : SensorEventListener {
        @SuppressLint("SetTextI18n")
        override fun onSensorChanged(event: SensorEvent) {

            accX = event.values[0]
            accY = event.values[1]
            accZ = event.values[2]

            val accelerometerValues = "Acceleration (m/s2) : \n\tX: %.2f Y: %.2f  Z: %.2f".format(
                accX, accY, accZ
            )
            accTextView.text = accelerometerValues


        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            // Handle accuracy changes if needed
        }
    }

    private val gyroscopeEventListener = object : SensorEventListener {
        @SuppressLint("SetTextI18n")
        override fun onSensorChanged(event: SensorEvent) {
            val angularSpeedX = event.values[0]
            val angularSpeedY = event.values[1]
            val angularSpeedZ = event.values[2]

            // You can convert radians to degrees if needed
            gyroX = Math.toDegrees(angularSpeedX.toDouble()).toFloat()
            gyroY = Math.toDegrees(angularSpeedY.toDouble()).toFloat()
            gyroZ = Math.toDegrees(angularSpeedZ.toDouble()).toFloat()


            val rotationValues = "Rotation : (°/s) \n \tX: %.2f Y: %.2f Z: %.2f".format(
                gyroX, gyroY, gyroZ
            )


            gyroValuesTextView.text = rotationValues


        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            // Handle accuracy changes if needed
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize MediaPlayer with the danger sound file
        mediaPlayer = MediaPlayer.create(this, R.raw.attention)
        setContentView(R.layout.activity_gyroscope)
        // Acquire a WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "YourApp:WakeLockTag"
        )
        wakeLock?.acquire()
        // Assuming you have a Toolbar in your layout with the id "toolbar"
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        val relativeLayout : RelativeLayout = findViewById(R.id.mainRelative)
        // Set the title
        toolbar.title = "Stay Safe"
        val colorAttr = if (isDarkTheme()) {
            R.color.darkToolbarBackgroundColor
        } else {
            R.color.lightToolbarBackgroundColor
        }
        val colorMain = if (isDarkTheme()) {
            R.color.darkBackgroundColor
        } else {
            R.color.lightBackgroundColor
        }
        relativeLayout.setBackgroundResource(colorMain)
        toolbar.setBackgroundResource(colorAttr)
        pieChart = findViewById(R.id.pieChart)

        // Initialize PieChart
        pieChart.setTouchEnabled(true);
        pieChart.isRotationEnabled = false;
        pieChart.description.isEnabled = false;
        pieChart.setDrawEntryLabels(false);
        pieChart.legend.isEnabled = false;
        pieChart.description.isEnabled = false
        pieChart.setUsePercentValues(false)
        pieChart.isDrawHoleEnabled = true
        pieChart.holeRadius = 65f
        pieChart.setEntryLabelColor(android.R.color.black)
        pieChart.setHoleColor(Color.TRANSPARENT)
        accTextView = findViewById(R.id.accValues)
        gyroValuesTextView = findViewById(R.id.gyrValues)
        predictionValuesTextView = findViewById(R.id.predictionValues)
        pieChart.setCenterTextSize(20f)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        // Initialize and register the accelerometer sensor
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        handler = Handler()
        val context = applicationContext
        model = DrivingBehaviorCnn.newInstance(context)
        if (gyroscopeSensor != null && accelerometerSensor != null) {
            sensorManager.registerListener(
                gyroscopeEventListener,
                gyroscopeSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            sensorManager.registerListener(
                accelerometerEventListener,
                accelerometerSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            timer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    handler.post(saveDataRunnable)
                }
            }, 0, 1000)

        } else {
            accTextView.text = "Sensor not available on this device"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(gyroscopeEventListener)
        model.close()
        // Release the WakeLock when the activity is destroyed
        wakeLock?.release()
        // Release MediaPlayer resources when the activity is destroyed
        mediaPlayer.release()
    }
    private fun findMaxIndex(array: FloatArray): Int {
        var maxIndex = 0
        var maxValue = array[0]
        for (i in 1 until array.size) {
            if (array[i] > maxValue) {
                maxValue = array[i]
                maxIndex = i
            }
        }
        return maxIndex
    }
    @SuppressLint("SetTextI18n")
    private fun predictingBehavior(){
        var lastPrediction = 0
        var allPrdictionsString : String
        // Create inputs for reference.
        val staticInputData = floatArrayOf(
            accX,accY,accZ,gyroX,gyroY,gyroZ
        )
        // Create a TensorBuffer with the static input data
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 6, 1), DataType.FLOAT32)
        inputFeature0.loadArray(staticInputData)
        // Runs model inference and gets the result.
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer
        var maxIndex = findMaxIndex(outputFeature0.floatArray)
        if(currentIndex<10){
            if(maxIndex == 0){
                maxIndex = 1
            }
            allPredictions.set(currentIndex,maxIndex)
            currentIndex++
        }else{
            currentIndex = 0
            val danger = findMostOccurringValue(allPredictions)
            if(danger == 2 ){
                playDangerAlert()
            }
        }
        lastPrediction = findMostOccurringValue(allPredictions)
        val predictionText = behaviorDescription(lastPrediction)
        predictionValuesTextView.text = "You are driving $predictionText. "
        pieChart.centerText = predictionText

        // Update PieChart with the latest prediction
        val entries = ArrayList<PieEntry>()
        entries.add(PieEntry(countOccurrences(allPredictions,0).toFloat(), "Normal"))
        entries.add(PieEntry(countOccurrences(allPredictions,1).toFloat(), "Normal"))
        entries.add(PieEntry(countOccurrences(allPredictions,2).toFloat(), "Danger"))
        val dataSet = PieDataSet(entries, "Predictions")

        val colors = listOf<Int>(Color.rgb(40, 167, 69), Color.rgb(40, 167, 69), Color.rgb(220, 53, 69))
        dataSet.colors = colors

        val data = PieData(dataSet)
        pieChart.data = data
        pieChart.invalidate()


    }
    fun findMostOccurringValue(arr: IntArray): Int {
        val counts = HashMap<Int, Int>()

        // Count occurrences of each value in the array
        for (value in arr) {
            counts[value] = counts.getOrDefault(value, 0) + 1
        }

        // Find the value with the highest count
        var mostCommonValue = 0
        var maxCount = 0
        for ((value, count) in counts) {
            if (count > maxCount) {
                maxCount = count
                mostCommonValue = value
            }
        }

        return mostCommonValue
    }
    fun behaviorDescription(prediction: Int): String {
        return when (prediction) {
            0 -> "normally"
            1 -> "normally"
            2 -> "aggressively"
            else -> "Unknown"
        }
    }
    // Function to play the danger sound
    private fun playDangerAlert() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
        }
    }
    fun countOccurrences(arr: IntArray, target: Int): Int {
        var count = 0
        for (value in arr) {
            if (value == target) {
                count++
            }
        }
        return count
    }
    private fun isDarkTheme(): Boolean {
        val currentNightMode =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }
    // Call this function when a danger prediction is detected
    private fun handleDangerPrediction() {
        // Your logic to handle the danger prediction...

        // Play the danger alert sound
        playDangerAlert()
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    WeDriveTheme {
        Greeting("Android")
    }
}