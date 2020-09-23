package com.parassidhu.tickingtimer

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.parassidhu.tickingtimer.Utils.dp2px
import kotlinx.coroutines.*
import kotlin.math.max
import com.parassidhu.tickingtimer.Config as Config

class TickingTimer(
    context: Context,
    attributeSet: AttributeSet?
) : FrameLayout(context, attributeSet), LifecycleObserver {

    // Duration for which timer is going to run
    private var timerDuration: Int = Defaults.timerDuration

    // Temp variable to save duration to restore later
    private var tempDuration: Int = 0

    // Used to automatically cancel job if view is destroyed
    private var lifecycleOwner: LifecycleOwner =
        context as? LifecycleOwner ?: throw IllegalStateException("$context isn't a LifecycleOwner")

    // Predefined shape to be used when custom background isn't provided
    private var shape: Shape = Defaults.shape

    // Animation during timer is running
    private var timerAnimation: Animation = Defaults.getTimerAnimation(scaleX, scaleY)

    /*
      Animation to be run on the end of the timer.
      Exposed view through a lambda to run animation on.
      Provided a setter function for this lambda
    */

    private var onTimerEndAnimation: ((View) -> Unit) = Defaults.onTimerEndAnimation

    // Lambda to be executed on end of the timer
    private var onFinished: (() -> Unit)? = null

    // Job which runs the timer
    private var job: Job? = null

    // Views
    private val timerText: TextView
    private val backgroundImage: ImageView
    private val itemView: FrameLayout

    init {
        View.inflate(context, R.layout.timer_view, this)
        timerText = findViewById(R.id.timerText)
        itemView = findViewById(R.id.itemView)
        backgroundImage = findViewById(R.id.backgroundImage)
        lifecycleOwner.lifecycle.addObserver(this)
    }

    // Start timer with config/customization
    inline fun start(func: TickingTimer.() -> Unit): TickingTimer = apply {
        func()
        start()
    }

    // Start timer without any config/customization
    fun start() {
        tempDuration = timerDuration
        timerText.text = timerDuration.toString()
        isVisible = true
        alpha = 1f

        job?.cancel()
        job = CoroutineScope(Dispatchers.Main).launch {
            this@TickingTimer.apply {
                startAnimation(timerAnimation)

                while (timerDuration > 0 && isActive) {
                    delay(1000)
                    decrementTimerText()

                    if (timerDuration == 0)
                        break

                    delay(1000)
                    decrementTimerText()
                }

                onTimerEndAnimation(this)
                onEnd()
            }
        }

        job?.invokeOnCompletion {
            timerDuration = tempDuration
            this.animation?.cancel()
        }
    }

    // Reset things at the end of the timer
    private fun onEnd() {
        scaleX = 1f
        scaleY = 1f
        onFinished?.invoke()
        timerDuration = tempDuration
    }

    // Sets timer duration in seconds
    fun timerDuration(duration: Int) {
        timerDuration = maxOf(0, duration)
    }

    // Sets color tint to the background
    fun backgroundTint(color: Int?) {
        backgroundImage.imageTintList = if (color == null) null else ColorStateList.valueOf(color)
    }

    // Sets custom background
    fun customBackground(@DrawableRes resource: Int) {
        backgroundImage.setImageResource(resource)
    }

    // If custom background isn't used, shape can be customized using given params
    fun shape(shape: Shape) {
        this.shape = shape

        when (shape) {
            Shape.CIRCLE -> backgroundImage.setImageResource(R.drawable.bg_timer_circle)
            Shape.ROUNDED -> backgroundImage.setImageResource(R.drawable.bg_timer_rounded)
            Shape.RECTANGLE -> backgroundImage.setImageResource(R.drawable.bg_timer_rectangle)
        }
    }

    // Sets the size of text in DP units
    fun textSize(@Dp textSize: Int) {
        timerText.textSize = dp2px(context, textSize)
    }

    // Sets the color of the text
    fun textColor(color: Int) {
        timerText.setTextColor(color)
    }

    // Customize the text styling by setting text appearance
    fun textAppearance(resId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            timerText.setTextAppearance(resId)
        else
            timerText.setTextAppearance(context, resId)
    }

    // Execute a piece of code on the end of timer
    fun onFinished(action: () -> Unit) {
        this.onFinished = action
    }

    // Customize the animation played during the timer
    fun timerAnimation(animation: Animation) {
        timerAnimation = animation
    }

    // Customize the timer end animation
    fun timerEndAnimation(action: (View) -> Unit) {
        onTimerEndAnimation = action
    }

    fun defaultConfig() = Config()

    fun applyConfig(config: Config) {
        timerDuration = config.timerDuration
        shape = config.shape
        timerAnimation = config.timerAnimation
        onTimerEndAnimation = config.onTimerEndAnimation

        backgroundTint(config.backgroundTint)

        config.customBackground?.let { bg -> customBackground(bg) }
        config.textSize?.let { size -> textSize(size) }

        textColor(config.textColor)

        config.textAppearance?.let { resId -> textAppearance(resId) }

        onFinished = config.onFinished
    }

    private fun decrementTimerText() {
        timerText.text = (max(timerText.text.toString().toInt() - 1, 0)).toString()
        timerDuration--
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private fun cancel() {
        job?.cancel()
    }
}