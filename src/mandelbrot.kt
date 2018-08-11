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
data class WindowCoordinate(val x: Int, val y: Int)
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
    }

    private fun updateView() {
        //This function is called whenever we pan or zoom. When we zoom we dont change the top-left
        //bounds so no need to update them. When we pan we have already changed those coordinates
        //so the only thing left to update in any case are the bottom and right bounds.
        val height: Double = currentZoomLevel * startHeight
        BOUND_BOTTOM = BOUND_TOP - height
        BOUND_RIGHT = BOUND_LEFT + (height * (3.0/2.0))
        redrawView()
    }

    private fun getOrthoCoordsFromWindowCoords(coords: WindowCoordinate): ComplexNumber {
        // The max and min bounds are taken from our BOUND_* variables we used to pass to glOrtho()
        // So to go from window cords to our ortho coords is:
        //
        // x_ortho = (x_window/width)*(x_ortho_max - x_ortho_min) + x_ortho_min
        //
        //The opposite way would be:
        //
        // X_window = (width*(x_ortho_min - x_ortho))/(x_ortho_min - x_ortho_max)
        //
        // Same for Y coord just different bounds

        val orthoX: Double = ((coords.x.toDouble()/WINDOW_SIZE_WIDTH.toDouble())*(BOUND_RIGHT - BOUND_LEFT)) + BOUND_LEFT
        val orthoY: Double = ((coords.y.toDouble()/WINDOW_SIZE_HEIGHT.toDouble())*(BOUND_TOP - BOUND_BOTTOM)) + BOUND_BOTTOM

        return ComplexNumber(orthoX, orthoY)
    }

    private fun mandelbrotsimple(): Map<WindowCoordinate, Color> {
        //will clean this up later
        //real and imaginary parts, x and y respectively.
        //Drawing from bottom left to top right
        val cordlist = mutableMapOf<WindowCoordinate, Color>()
        //Scanning left to right then bottom to top
        //outer loop, top to bottom, Imaginary coord, y

        var curYcoordinate = 0
        while (curYcoordinate in 0..WINDOW_SIZE_HEIGHT) {
            var curXcoordinate = 0
            while (curXcoordinate in 0..WINDOW_SIZE_WIDTH) {
                //doing stuffs, mandelbrot-ey stuff

                //So now that I'm iterating over window coordinates instead of the imaginary plane coords I have to figure out the pixel
                //sizing on my own. Best I've come up with so far but I'm sure there is better.

                //First calculate this pixels color, then set the pixel color around us to the same depending on the point size
                val curWindowCoords = WindowCoordinate(curXcoordinate, curYcoordinate)
                val escapeVelocityColor: Color = findEscapeVelocity(getOrthoCoordsFromWindowCoords(curWindowCoords))
                cordlist[curWindowCoords] = escapeVelocityColor
                // Now run the calculations for surrounding pixels if necessary
                if (POINT_SIZE > 1) {
                    for (dy in 0 until POINT_SIZE)
                        for (dx in 1..POINT_SIZE)
                            cordlist[WindowCoordinate(curWindowCoords.x+dx, curWindowCoords.y+dy)] = escapeVelocityColor
                }
                curXcoordinate += POINT_SIZE
            }
            curYcoordinate += POINT_SIZE
        }
        println("Finished iterating over the Imaginary axis.")
        return cordlist
    }

    fun redrawView() {
        glPointSize(POINT_SIZE.toFloat())
        glClear(GL_COLOR_BUFFER_BIT)
        glBegin(GL_POINTS)
        val s = mandelbrotsimple()
        for ((cord, color) in s) {
            glColor3d(color.r, color.g, color.b)
            glVertex2i(cord.x, cord.y)
        }
        glEnd()
        glfwSwapBuffers(window)
    }
}

//Check if a complex number is bounded or escapes.
//If it escapes (is greater than 2) it returns a color defined by its escaped velocity (ratio between current iteration number and max iterations).
//If its bounded it returns black, which means that point was in the mandelbrot set.
fun findEscapeVelocity(c: ComplexNumber): Color {
    var z = ComplexNumber(0.0, 0.0)
    for (iter in 1..ESCAPE_VELOCITY_TEST_ITERATIONS) {
        if (z.magnitude() > 2.0) {
            return Color(iter.toDouble()/ESCAPE_VELOCITY_TEST_ITERATIONS.toDouble(),0.0, tan(iter.toDouble()/ESCAPE_VELOCITY_TEST_ITERATIONS.toDouble()))
        }
        z = z*z + c
    }
    return Color(0.0,0.0,0.0) //Black color, inside the set
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
    //GL11.glOrtho(-2.0, 1.0, -1.0, 1.0, -1.0, 1.0)
    GL11.glOrtho(0.0, WINDOW_SIZE_WIDTH.toDouble(), 0.0, WINDOW_SIZE_HEIGHT.toDouble(), -1.0, 1.0)
    glfwShowWindow(window)
}