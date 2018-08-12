import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryUtil
import java.lang.Math.pow
import kotlin.math.*

//Zoom/pan increment stuffs
const val ZOOM_INCREMENT = 0.3 //increase by X%
const val PAN_PCT_INCREMENT = 0.25 //Move view by X%

//GL STUFFS
const val WINDOW_SIZE_WIDTH = 1680
const val WINDOW_SIZE_HEIGHT = 1050

var window: Long = NULL

//This defines the resolution limit of our drawing.
// A value of 1 draws every single pixel
// A larger values increases the size of each point drawn (decreasing resolution).
var POINT_SIZE: Int = 3

//How many iterations should we run before we are certain of an escape velocity?
var ESCAPE_VELOCITY_TEST_ITERATIONS: Int = 100

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
    private var currentZoomLevelInt: Int = 1 //Just for looks
    private var startHeight: Double = 2.0
    private var BOUND_TOP: Double = 1.0
    private var BOUND_BOTTOM: Double = -1.0
    private var BOUND_LEFT: Double = -2.0
    private var BOUND_RIGHT: Double = 1.0
    private var currentOrthoCoordinates = ComplexNumber(-0.5, 0.0)

    @Suppress("UNUSED_PARAMETER")
    private fun glfwKeypressCallback(window: Long, key: Int, scancode: Int, action: Int, mods: Int) {
        if (action == GLFW_PRESS || action == GLFW_REPEAT) {
            //needed later
            val moveAmount: Double = currentZoomLevel*startHeight/2.0*PAN_PCT_INCREMENT
            when (key) {
                GLFW_KEY_UP -> {
                    currentOrthoCoordinates = ComplexNumber(currentOrthoCoordinates.real, currentOrthoCoordinates.imag + moveAmount)
                    updateView()
                }
                GLFW_KEY_DOWN -> {
                    currentOrthoCoordinates = ComplexNumber(currentOrthoCoordinates.real, currentOrthoCoordinates.imag - moveAmount)
                    updateView()
                }
                GLFW_KEY_LEFT -> {
                    currentOrthoCoordinates = ComplexNumber(currentOrthoCoordinates.real - moveAmount, currentOrthoCoordinates.imag)
                    updateView()
                }
                GLFW_KEY_RIGHT -> {
                    currentOrthoCoordinates = ComplexNumber(currentOrthoCoordinates.real + moveAmount, currentOrthoCoordinates.imag)
                    updateView()
                }
                GLFW_KEY_KP_ADD -> {
                    currentZoomLevel += currentZoomLevel * ZOOM_INCREMENT
                    currentZoomLevelInt--
                    updateView()
                }
                GLFW_KEY_KP_SUBTRACT -> {
                    currentZoomLevel -= currentZoomLevel * ZOOM_INCREMENT
                    currentZoomLevelInt++
                    updateView()
                }
                GLFW_KEY_LEFT_BRACKET -> {
                    POINT_SIZE--
                    if (POINT_SIZE == 0) POINT_SIZE = 1
                    updateView()
                }
                GLFW_KEY_RIGHT_BRACKET -> {
                    POINT_SIZE++
                    updateView()
                }
                GLFW_KEY_MINUS -> {
                    ESCAPE_VELOCITY_TEST_ITERATIONS -= 50
                    if (ESCAPE_VELOCITY_TEST_ITERATIONS < 0) ESCAPE_VELOCITY_TEST_ITERATIONS = 0
                    updateView()
                }
                GLFW_KEY_EQUAL -> {
                    ESCAPE_VELOCITY_TEST_ITERATIONS += 50
                    updateView()
                }

                GLFW_KEY_KP_0 -> resetAll()
            }
        }
    }

    private fun resetAll() {
        BOUND_TOP = 1.0
        BOUND_LEFT = -2.0
        currentZoomLevel = 1.0
        currentOrthoCoordinates = ComplexNumber(-0.5, 0.0)
        updateView()
    }

    private fun updateView() {
        val height: Double = currentZoomLevel * startHeight
        val width: Double = height*3.0/2.0
        BOUND_LEFT = currentOrthoCoordinates.real-width/2
        BOUND_TOP = currentOrthoCoordinates.imag+height/2
        BOUND_BOTTOM = BOUND_TOP - height
        BOUND_RIGHT = BOUND_LEFT + width
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

    private fun mandelbrotsimple() {
        val starttime = System.currentTimeMillis()

        glPointSize(POINT_SIZE.toFloat())
        glClear(GL_COLOR_BUFFER_BIT)
        glBegin(GL_POINTS)

        //Scanning left to right then bottom to top
        //outer loop, top to bottom, Imaginary coord, y
        var calcnumber: Long = 0
        var curYcoordinate = 0
        while (curYcoordinate in 0..WINDOW_SIZE_HEIGHT) {
            var curXcoordinate = 0
            while (curXcoordinate in 0..WINDOW_SIZE_WIDTH) {
                calcnumber += 1
                //doing stuffs, mandelbrot-ey stuff

                //So now that I'm iterating over window coordinates instead of the imaginary plane coords I have to figure out the pixel
                //sizing on my own. Best I've come up with so far but I'm sure there is better.
                //First calculate this pixels color, then set the pixel color around us to the same depending on the point size

                val curWindowCoords = WindowCoordinate(curXcoordinate, curYcoordinate)
                val escapeVelocityColor: Color = findEscapeVelocity(getOrthoCoordsFromWindowCoords(curWindowCoords))

                glColor3d(escapeVelocityColor.r, escapeVelocityColor.g, escapeVelocityColor.b)
                glVertex2i(curWindowCoords.x, curWindowCoords.y)
                // Now set the color for surrounding pixels if necessary

                if (POINT_SIZE > 1) {
                    for (dy in 0 until POINT_SIZE)
                        for (dx in 0 until POINT_SIZE)
                            glVertex2i(curWindowCoords.x+dx, curWindowCoords.y+dy)
                }

                curXcoordinate += POINT_SIZE
            }
            curYcoordinate += POINT_SIZE
        }
        glEnd()
        glfwSwapBuffers(window)
        println("Done! Took ${(System.currentTimeMillis() - starttime)} milliseconds to generate $calcnumber pixels")
        glfwSetWindowTitle(window, "Mandelbrot Set :: (${currentOrthoCoordinates.real}, ${currentOrthoCoordinates.imag}) :: Zoom Level: $currentZoomLevelInt :: Point size: $POINT_SIZE ::  Max iterations: $ESCAPE_VELOCITY_TEST_ITERATIONS")
    }

    fun redrawView() {
        println("Generating simple Mandelbrot set at Coords: (${currentOrthoCoordinates.real}, ${currentOrthoCoordinates.imag})  Zoomlevel: $currentZoomLevelInt  Point size: $POINT_SIZE  Max iterations: $ESCAPE_VELOCITY_TEST_ITERATIONS  (this could take a while)...")
        glfwSetWindowTitle(window, "Mandelbrot Set :: (${currentOrthoCoordinates.real}, ${currentOrthoCoordinates.imag}) :: Zoom Level: $currentZoomLevelInt} :: Point size: $POINT_SIZE :: Max iterations: $ESCAPE_VELOCITY_TEST_ITERATIONS  ::  Generating...")
        mandelbrotsimple()
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