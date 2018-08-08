import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryUtil
import java.lang.Math.pow
import kotlin.math.sqrt
import kotlin.math.tan

//Zoom/pan increment stuffs
const val ZOOM_INCREMENT = 0.3 //increase by 0.3
const val PAN_PCT_INCREMENT = 0.25 //Move view by 25%

//GL STUFFS
const val WINDOW_SIZE_WIDTH = 1680
const val WINDOW_SIZE_HEIGHT = 1050

var window: Long = NULL

//This defines the resolution limit of our drawing.
// A value of 1 draws every single pixel
// A larger values increases the size of each point drawn (decreasing resolution).
const val POINT_SIZE: Int = 2

//How many iterations should we run before we are certain of an escape velocity?
const val ESCAPE_VELOCITY_TEST_ITERATIONS: Int = 50

data class Color(val r: Double, val g: Double, val b: Double)

data class ComplexNumber(val real: Double, val imag: Double) {
    operator fun minus(c: ComplexNumber): ComplexNumber { return ComplexNumber(real-c.real, imag-c.imag)}
    operator fun plus(c: ComplexNumber): ComplexNumber { return ComplexNumber(real+c.real, imag+c.imag)}
    operator fun times(c: ComplexNumber): ComplexNumber {
        val imagpart: Double = real * c.imag + imag * c.real
        val realpart: Double = real * c.real + imag * c.imag * -1.0
        return ComplexNumber(realpart, imagpart)
    }
    fun magnitude(): Double {
        if (real == 0.0 && imag == 0.0) return 0.0
        else return sqrt(pow(real, 2.0) + pow(imag, 2.0))
    }
}


class MandelbrotView(private val window: Long) {
    init {
        glfwSetKeyCallback(window, this::glfwKeypressCallback)
    }

    private var currentZoomLevel: Double = 1.0 //zoomed out at 100%
    private var startHeight: Double = 2.0
    //Coords defined by top-left corner
    private var BOUND_TOP: Double = 1.0
    private var BOUND_BOTTOM: Double = -1.0
    private var BOUND_LEFT: Double = -2.0
    private var BOUND_RIGHT: Double = 1.0
    private var RESOLUTION_LIMIT_X: Double = -1.0
    private var RESOLUTION_LIMIT_Y: Double = -1.0
    
    private fun calcResolutionLimit() {
        //Calculate our resolution limits based on the defined point size
        val xslices: Double = WINDOW_SIZE_WIDTH/POINT_SIZE.toDouble()
        val yslices: Double = WINDOW_SIZE_HEIGHT/POINT_SIZE.toDouble()
        RESOLUTION_LIMIT_X = (BOUND_RIGHT-BOUND_LEFT)/xslices
        RESOLUTION_LIMIT_Y = (BOUND_TOP-BOUND_BOTTOM)/yslices
    }


    @Suppress("UNUSED_PARAMETER")
    private fun glfwKeypressCallback(window: Long, key: Int, scancode: Int, action: Int, mods: Int) {
        if (action == GLFW_PRESS || action == GLFW_REPEAT)
            when (key) {
                GLFW_KEY_UP -> {
                    BOUND_TOP += (BOUND_TOP-BOUND_BOTTOM)/2*PAN_PCT_INCREMENT
                }
                GLFW_KEY_DOWN -> {
                    BOUND_TOP -= (BOUND_TOP-BOUND_BOTTOM)/2*PAN_PCT_INCREMENT
                }
                GLFW_KEY_LEFT -> {
                    BOUND_LEFT -= (BOUND_TOP-BOUND_BOTTOM)/2*PAN_PCT_INCREMENT
                }
                GLFW_KEY_RIGHT -> {
                    BOUND_LEFT += (BOUND_TOP-BOUND_BOTTOM)/2*PAN_PCT_INCREMENT
                }
                GLFW_KEY_KP_ADD -> currentZoomLevel += currentZoomLevel*ZOOM_INCREMENT
                GLFW_KEY_KP_SUBTRACT -> currentZoomLevel -= currentZoomLevel*ZOOM_INCREMENT
                GLFW_KEY_KP_0 -> resetAll()
            }
        updateView()
    }

    private fun resetAll() {
        BOUND_TOP = 1.0
        BOUND_LEFT = -2.0
        currentZoomLevel = 1.0
        updateView()
    }

    private fun updateView() {
        //This function is called whenever we pan or zoom. When we zoom we dont change the top-left
        //bounds so no need to update them. When we pan we have already changed those coordinates
        //so the only thing left to update in any case are the bottom and right bounds.
        val height: Double = currentZoomLevel * startHeight
        BOUND_BOTTOM = BOUND_TOP - height
        BOUND_RIGHT = BOUND_LEFT + (height * (3.0/2.0))
        /*
        ABSOLUTELY CRITICAL CALL! I was pulling my hair out over how to get pan/zoom working and
        nothing was working correctly until I added this single call. Without this, calls to glOrtho
        just modify the current projection matrix. By calling glLoadIdentity(), we load the identity
        matrix first, essentially resetting us to the default state, before we reset our ortho matrix.
        */
        glLoadIdentity()
        GL11.glOrtho(BOUND_LEFT, BOUND_RIGHT, BOUND_BOTTOM, BOUND_TOP, -1.0, 1.0)
        redrawView()
    }

    private fun mandelbrotsimple(): Map<ComplexNumber, Color> {
        //will clean this up later
        //real and imaginary parts, x and y respectively.
        //Starting in the top left (-2, 1)
        calcResolutionLimit()
        var curImagCoord: Double = BOUND_TOP
        val cordlist = mutableMapOf<ComplexNumber, Color>()
        //Scanning left to right then top to bottom
        //outer loop, top to bottom, Imaginary coord, y
        while (curImagCoord in BOUND_BOTTOM..BOUND_TOP) {
            var real: Double = BOUND_LEFT
            while (real in BOUND_LEFT..BOUND_RIGHT) {
                //doing stuffs, mandelbrot-ey stuff
                val escapevelocity: Double = findEscapeVelocity(ComplexNumber(real, curImagCoord))
                if (escapevelocity == 0.0) {
                    cordlist[ComplexNumber(real, curImagCoord)] = Color(0.0,0.0,0.0) //Black color, inside the set
                }
                else {
                    cordlist[ComplexNumber(real, curImagCoord)] = Color(escapevelocity/ESCAPE_VELOCITY_TEST_ITERATIONS,0.0, tan(escapevelocity/ESCAPE_VELOCITY_TEST_ITERATIONS))
                }
                real += RESOLUTION_LIMIT_X
            }

            curImagCoord -= RESOLUTION_LIMIT_Y
        }
        println("Finished iterating over the Imaginary axis.")
        return cordlist
    }

    fun redrawView() {
        glPointSize(6.0f)
        glClear(GL_COLOR_BUFFER_BIT)
        glBegin(GL_POINTS)
        val s = mandelbrotsimple()
        for ((cord, color) in s) {
            glColor3d(color.r, color.g, color.b)
            glVertex2d(cord.real, cord.imag)
        }
        glEnd()
        glfwSwapBuffers(window)
    }
}

//Check if a complex number is bounded or escapes.
//If it escapes (is greater than 2) it returns the the current iteration number.
//If its bounded it returns 0
fun findEscapeVelocity(c: ComplexNumber): Double {
    var z = ComplexNumber(0.0, 0.0)
    for (iter in 1..ESCAPE_VELOCITY_TEST_ITERATIONS) {
        if (z.magnitude() > 2.0) {
            return iter.toDouble()
        }
        z = z*z + c
    }
    return 0.0
}

fun main(args: Array<String>) {
    init()
    println("Generating simple Mandelbrot set (this could take a while)...")
    val viewControl = MandelbrotView(window)
    viewControl.redrawView()
    //and wait for any keyboard stuffs now
    while (!glfwWindowShouldClose(window)) {
        glfwPollEvents()
        Thread.sleep(50) //only paint every 100ms
    }
}

fun init(windowSizeW: Int = WINDOW_SIZE_WIDTH, windowSizeH: Int = WINDOW_SIZE_HEIGHT) {
    if ( !glfwInit()) {
        throw Exception("Failed to initialize GLFW.")
    }
    glfwDefaultWindowHints()
    //Do not allow resize
    glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
    glfwWindowHint(GLFW_DOUBLEBUFFER, GLFW_TRUE)
    window = glfwCreateWindow(windowSizeW, windowSizeH, "Mandelbrot Set", 0, 0)
    if (window == MemoryUtil.NULL) {
        throw Exception("Failed to initialize window.")
    }
    glfwMakeContextCurrent(window)
    // GL configuration comes AFTER we make the window our current context, otherwise errors
    GL.createCapabilities()
    GL11.glClearColor(1.0f, 1.0f, 1.0f, 1.0f) //white background
    GL11.glViewport(0, 0, WINDOW_SIZE_WIDTH, WINDOW_SIZE_HEIGHT)
    GL11.glMatrixMode(GL_PROJECTION)
    GL11.glOrtho(-2.0, 1.0, -1.0, 1.0, -1.0, 1.0)
    glfwShowWindow(window)
}