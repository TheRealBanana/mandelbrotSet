import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.glFinish
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL40
import org.kylesplace.kotlin.GLhelperfunctions.*


//Zoom/pan increment stuffs
const val ZOOM_INCREMENT = 0.3 //increase by X%
const val PAN_PCT_INCREMENT = 0.25 //Move view by X%
const val ITERATION_INCREMENT = 20

//GL STUFFS
//Locking aspect ratio to 3:2
const val WINDOW_SIZE_WIDTH = 1680
const val WINDOW_SIZE_HEIGHT = (WINDOW_SIZE_WIDTH*(2.0/3.0)).toInt()
var window: Long = NULL

//How many iterations should we run before we are certain of an escape velocity?
var ESCAPE_VELOCITY_TEST_ITERATIONS: Int = 500

data class WindowCoordinate(val x: Int, val y: Int)
data class ComplexNumber(val real: Double, val imag: Double)

class MandelbrotView(private val window: Long) {
    init {
        GLFW.glfwSetKeyCallback(window, this::glfwKeypressCallback)
        GLFW.glfwSetMouseButtonCallback(window, this::glfwMouseClickCallback)
        setupShader()

    }
    private var currentColorMode: Int = 0 // color modes can be changed with the keyboard
    private var currentZoomLevel: Double = 1.0 //zoomed out at 100%
    private var currentZoomLevelInt: Int = 1 //Just for looks
    private var startHeight: Double = 2.0
    private var BOUND_TOP: Double = 1.0
    private var BOUND_BOTTOM: Double = -1.0
    private var BOUND_LEFT: Double = -2.0
    private var BOUND_RIGHT: Double = 1.0
    private var currentOrthoCoordinates = ComplexNumber(-0.5, 0.0)
    //private var currentOrthoCoordinates = ComplexNumber(0.3866831889092910, 0.5696433852559384)
    //Weird square thing @ 200 iterations and zoom ~75
    //private var currentOrthoCoordinates = ComplexNumber(-1.632329465459738, 0.022135094625989362)
    //Coords from the deep zoom on wikipedia
    //private var currentOrthoCoordinates = ComplexNumber(-0.743643887037158704752191506114774, 0.131825904205311970493132056385139)
    // Shader stuffs
    private var uloc_WINDOW_SIZE_WIDTH: Int = 0
    private var uloc_WINDOW_SIZE_HEIGHT: Int = 0
    private var uloc_CURRENT_COLOR_MODE: Int = 0
    private var uloc_ESCAPE_VELOCITY_TEST_ITERATIONS: Int = 0
    private var uloc_ORTHO_WIDTH: Int = 0
    private var uloc_ORTHO_HEIGHT: Int = 0
    private var uloc_BOUND_LEFT: Int = 0
    private var uloc_BOUND_BOTTOM: Int = 0

    private fun setupShader() {
        //Lets create our shader program and attach our shaders
        //Shader program that contains all our shaders
        val shaderProgram = GL20.glCreateProgram()
        //Fragment Shader
        val fragmentShader = createShaderFromFile("src/mandelbrot_fp64.frag", GL20.GL_FRAGMENT_SHADER)
        //Finally attach our compiled shaders to our shader program and use it
        GL20.glAttachShader(shaderProgram, fragmentShader)
        //Linky linky
        GL20.glLinkProgram(shaderProgram)
        //Usey Usey
        GL20.glUseProgram(shaderProgram)
        //Uniform locations only change on shader programing linking so this doesnt have to be run every loop iteration
        uloc_WINDOW_SIZE_WIDTH = GL20.glGetUniformLocation(shaderProgram, "WINDOW_SIZE_WIDTH")
        uloc_WINDOW_SIZE_HEIGHT = GL20.glGetUniformLocation(shaderProgram, "WINDOW_SIZE_HEIGHT")
        uloc_CURRENT_COLOR_MODE = GL20.glGetUniformLocation(shaderProgram, "CURRENT_COLOR_MODE")
        uloc_ESCAPE_VELOCITY_TEST_ITERATIONS = GL20.glGetUniformLocation(shaderProgram, "ESCAPE_VELOCITY_TEST_ITERATIONS")
        uloc_ORTHO_WIDTH = GL20.glGetUniformLocation(shaderProgram, "ORTHO_WIDTH")
        uloc_ORTHO_HEIGHT = GL20.glGetUniformLocation(shaderProgram, "ORTHO_HEIGHT")
        uloc_BOUND_LEFT = GL20.glGetUniformLocation(shaderProgram, "BOUND_LEFT")
        uloc_BOUND_BOTTOM = GL20.glGetUniformLocation(shaderProgram, "BOUND_BOTTOM")
    }

    private fun updateShaderUniforms() {
        GL20.glUniform1i(uloc_WINDOW_SIZE_WIDTH, WINDOW_SIZE_WIDTH)
        GL20.glUniform1i(uloc_WINDOW_SIZE_HEIGHT, WINDOW_SIZE_HEIGHT)
        GL20.glUniform1i(uloc_CURRENT_COLOR_MODE, currentColorMode)
        GL20.glUniform1i(uloc_ESCAPE_VELOCITY_TEST_ITERATIONS, ESCAPE_VELOCITY_TEST_ITERATIONS)
        GL40.glUniform1d(uloc_ORTHO_WIDTH, getOrthoWidth())
        GL40.glUniform1d(uloc_ORTHO_HEIGHT, getOrthoHeight())
        GL40.glUniform1d(uloc_BOUND_LEFT, BOUND_LEFT)
        GL40.glUniform1d(uloc_BOUND_BOTTOM, BOUND_BOTTOM)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun glfwMouseClickCallback(window: Long, button: Int, action: Int, mods: Int) {
        if (action == GLFW.GLFW_PRESS)
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
                //Get coords of mouse press event and convert them from window coords to ortho coords
                //Bleh, I hate using buffers.
                val xbuff = BufferUtils.createDoubleBuffer(1)
                val ybuff = BufferUtils.createDoubleBuffer(1)
                GLFW.glfwGetCursorPos(window, xbuff, ybuff)
                //So, just for funzies, GLFW's returned click coords use the top-left corner as the origin
                //instead of the OpenGL standard of the bottom left. Easily dealth with however
                val reversedYCoord: Int = WINDOW_SIZE_HEIGHT - ybuff.get(0).toInt()
                val clickWindowCoords = WindowCoordinate(xbuff.get(0).toInt(), reversedYCoord)
                val clickOrthoCoords: ComplexNumber = getOrthoCoordsFromWindowCoords(clickWindowCoords)
                currentOrthoCoordinates = clickOrthoCoords
                println("Setting coordinates to: (${clickOrthoCoords.real}. ${clickOrthoCoords.imag})")
                updateView()
            }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun glfwKeypressCallback(window: Long, key: Int, scancode: Int, action: Int, mods: Int) {
        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            //needed later
            val moveAmount: Double = currentZoomLevel*startHeight/2.0*PAN_PCT_INCREMENT
            when (key) {
                GLFW.GLFW_KEY_UP -> {
                    currentOrthoCoordinates = ComplexNumber(currentOrthoCoordinates.real, currentOrthoCoordinates.imag + moveAmount)
                    updateView()
                }
                GLFW.GLFW_KEY_DOWN -> {
                    currentOrthoCoordinates = ComplexNumber(currentOrthoCoordinates.real, currentOrthoCoordinates.imag - moveAmount)
                    updateView()
                }
                GLFW.GLFW_KEY_LEFT -> {
                    currentOrthoCoordinates = ComplexNumber(currentOrthoCoordinates.real - moveAmount, currentOrthoCoordinates.imag)
                    updateView()
                }
                GLFW.GLFW_KEY_RIGHT -> {
                    currentOrthoCoordinates = ComplexNumber(currentOrthoCoordinates.real + moveAmount, currentOrthoCoordinates.imag)
                    updateView()
                }
                GLFW.GLFW_KEY_KP_ADD -> {
                    currentZoomLevel += currentZoomLevel * ZOOM_INCREMENT
                    currentZoomLevelInt--
                    updateView()
                }
                GLFW.GLFW_KEY_KP_SUBTRACT -> {
                    currentZoomLevel -= currentZoomLevel * ZOOM_INCREMENT
                    currentZoomLevelInt++
                    updateView()
                }
                /*
                Point size is not used for now, might be removed completely.
                GLFW.GLFW_KEY_LEFT_BRACKET -> {
                    POINT_SIZE--
                    if (POINT_SIZE == 0) POINT_SIZE = 1
                    updateView()
                }
                GLFW.GLFW_KEY_RIGHT_BRACKET -> {
                    POINT_SIZE++
                    updateView()
                }
                */
                GLFW.GLFW_KEY_MINUS -> {
                    ESCAPE_VELOCITY_TEST_ITERATIONS -= ITERATION_INCREMENT
                    if (ESCAPE_VELOCITY_TEST_ITERATIONS < 0) ESCAPE_VELOCITY_TEST_ITERATIONS = 0
                    updateView()
                }
                GLFW.GLFW_KEY_EQUAL -> {
                    ESCAPE_VELOCITY_TEST_ITERATIONS += ITERATION_INCREMENT
                    updateView()
                }
                //Color mode keys, A and S for now
                //Basic color modes 0, 1, and 2
                GLFW.GLFW_KEY_A -> {
                    if (currentColorMode > 1) currentColorMode = 0
                    else currentColorMode++
                    updateView()
                }
                //Trig color modes 3 - 8
                GLFW.GLFW_KEY_S -> {
                    when {
                        currentColorMode < 3 -> currentColorMode = 3
                        currentColorMode > 7 -> currentColorMode = 3
                        else -> currentColorMode++
                    }
                    updateView()
                }
                GLFW.GLFW_KEY_KP_0 -> resetAll()
            }
        }
    }

    private fun resetAll() {
        currentColorMode = 0
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

    private fun getOrthoHeight(): Double { return currentZoomLevel * startHeight }
    private fun getOrthoWidth(): Double { return getOrthoHeight()*3.0/2.0}
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

        val orthoX: Double = ((coords.x.toDouble()/WINDOW_SIZE_WIDTH)*getOrthoWidth()) + BOUND_LEFT
        val orthoY: Double = ((coords.y.toDouble()/WINDOW_SIZE_HEIGHT)*getOrthoHeight()) + BOUND_BOTTOM

        return ComplexNumber(orthoX, orthoY)
    }



    fun redrawView() {
        val starttime = System.currentTimeMillis()
        println("Generating simple Mandelbrot set at Coords: (${currentOrthoCoordinates.real}, ${currentOrthoCoordinates.imag})  Zoomlevel: $currentZoomLevelInt  Max iterations: $ESCAPE_VELOCITY_TEST_ITERATIONS  ::  Color Mode: $currentColorMode  (this could take a while)...")
        GLFW.glfwSetWindowTitle(window, "Mandelbrot Set :: (${currentOrthoCoordinates.real}, ${currentOrthoCoordinates.imag}) :: Zoom Level: $currentZoomLevelInt} :: Max iterations: $ESCAPE_VELOCITY_TEST_ITERATIONS  ::  Color Mode: $currentColorMode  ::  Generating...")
        updateShaderUniforms()
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glVertex2f(-1.0f, 1.0f)
        GL11.glVertex2f(1.0f, 1.0f)
        GL11.glVertex2f(1.0f, -1.0f)
        GL11.glVertex2f(-1.0f, -1.0f)
        GL11.glEnd()
        GLFW.glfwSwapBuffers(window)
        //Need this call otherwise our timer returns 0
        glFinish()
        println("Done! Took ${(System.currentTimeMillis() - starttime)} milliseconds.")
        GLFW.glfwSetWindowTitle(window, "Mandelbrot Set :: (${currentOrthoCoordinates.real}, ${currentOrthoCoordinates.imag}) :: Zoom Level: $currentZoomLevelInt ::  Max iterations: $ESCAPE_VELOCITY_TEST_ITERATIONS  ::  Color Mode: $currentColorMode")
    }
}

fun main(args: Array<String>) {
    window = glinit(WINDOW_SIZE_WIDTH, WINDOW_SIZE_HEIGHT, "Mandelbrot Set")
    val viewControl = MandelbrotView(window)
    viewControl.redrawView()
    //and wait for any keyboard stuffs now
    while (!GLFW.glfwWindowShouldClose(window)) {
        GLFW.glfwPollEvents()
        Thread.sleep(50) //only paint every 100ms
    }
}
