import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryUtil
import java.lang.Math.pow
import kotlin.math.sqrt

//auto calculate point size by looking at resolution limit?
// distance between two points separated by resolution limit
// have to calculate the actual display coordinates from the point locations


//GL STUFFS
const val WINDOW_SIZE_WIDTH = 1280
const val WINDOW_SIZE_HEIGHT = 900

var window: Long = NULL

//WORRY ABOUT RESOLUTION (diff per iteration x and y)
const val RESOLUTION_LIMIT_Y: Double = 0.01
const val RESOLUTION_LIMIT_X: Double = 0.01

//How many iterations should we run before we are certain of an escape velocity?
const val ESCAPE_VELOCITY_TEST_ITERATIONS: Int = 100

data class Color(val r: Double, val g: Double, val b: Double)

data class ComplexNumber(var real: Double, var imag: Double) {
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

fun init(windowSizeW: Int = WINDOW_SIZE_WIDTH, windowSizeH: Int = WINDOW_SIZE_HEIGHT) {
    if ( !glfwInit()) {
        throw Exception("Failed to initialize GLFW.")
    }
    glfwDefaultWindowHints()
    //Do not allow resize
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
    glfwWindowHint(GLFW_DOUBLEBUFFER, GLFW_TRUE)
    window = glfwCreateWindow(windowSizeW, windowSizeH, "Mandelbrot Set", 0, 0)
    if (window == MemoryUtil.NULL) {
        throw Exception("Failed to initialize window.")
    }
    glfwMakeContextCurrent(window)

    //Key callbacks come later
    //glfwSetKeyCallback(window, object_with_keycallback_funcs::glfwKeypressCallbackFunc)

    // GL configuration comes AFTER we make the window our current context, otherwise errors
    GL.createCapabilities()
    GL11.glClearColor(1.0f, 1.0f, 1.0f, 1.0f) //white background
    GL11.glOrtho(-2.0, 1.0, -1.0, 1.0, -1.0, 1.0)
    GL11.glViewport(0, 0, WINDOW_SIZE_WIDTH, WINDOW_SIZE_HEIGHT)
    glfwShowWindow(window)
}

//Check if a complex number is bounded or escapes.
//If it escapes (is greater than 2) it returns the velocity.
//If its bounded it returns 0
fun findEscapeVelocity(c: ComplexNumber): Double {
    var z: ComplexNumber = ComplexNumber(0.0, 0.0)
    for (iter in 1..ESCAPE_VELOCITY_TEST_ITERATIONS) {
        if (z.magnitude() > 2.0) {
            return iter.toDouble()
        }
        z = z*z + c
    }
    return 0.0
}

fun mandelbrotsimple(): Map<ComplexNumber, Color> {
    //will clean this up later

    //real and imaginary parts, x and y respectively.
    //Starting in the top left (-2, 1)
    var curImagCoord: Double = 1.0

    val cordlist = mutableMapOf<ComplexNumber, Color>()
    //Scanning left to right then top to bottom
    //outer loop, top to bottom, Imaginary coord, y
    while (curImagCoord in -1.0..1.0) {
        var real: Double = -2.0
        while (real in -2.0..1.0) {
            //doing stuffs, mandelbrot-ey stuff
            val escapevelocity: Double = findEscapeVelocity(ComplexNumber(real, curImagCoord))
            if (escapevelocity == 0.0) {
                cordlist[ComplexNumber(real, curImagCoord)] = Color(0.0,0.0,0.0) //Black color, inside the set
            } //in the set, bounded value
            else {
                cordlist[ComplexNumber(real, curImagCoord)] = Color(escapevelocity/ESCAPE_VELOCITY_TEST_ITERATIONS,0.0,0.0) //Black color, inside the set
            }
            real += RESOLUTION_LIMIT_X
        }
        curImagCoord -= RESOLUTION_LIMIT_Y
    }
    println("Finished iterating over the Imaginary axis.")
    println("Mandelbrot tiem?")
    return cordlist
}

fun main(args: Array<String>) {
    init()
    println("Generating simple Mandelbrot set (not coloring)")
    val setcoords = mandelbrotsimple()
    println("Done generating set, printing to window...")
    while (!glfwWindowShouldClose(window)) {
        glfwPollEvents()
        glPointSize(5.0f)
        glClear(GL_COLOR_BUFFER_BIT)
        glBegin(GL_POINTS)
        for ((cord, color) in setcoords) {
            glColor3d(color.r, color.g, color.b)
            glVertex2d(cord.real, cord.imag)
        }
        glEnd()
        glfwSwapBuffers(window)
        //glFlush()
        Thread.sleep(100) //only paint every 100ms
    }
}