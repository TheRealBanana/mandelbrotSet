import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.glFinish
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL40
import java.lang.Math.pow

//Zoom/pan increment stuffs
var ZOOM_INCREMENT = 0.3 //increase by X%
const val PAN_PCT_INCREMENT = 0.25 //Move view by X%
const val ITERATION_INCREMENT = 20

//GL STUFFS
//Locking aspect ratio to 3:2
const val WINDOW_SIZE_WIDTH = 1680
const val WINDOW_SIZE_HEIGHT = (WINDOW_SIZE_WIDTH*(2.0/3.0)).toInt()
var window: Long = NULL

//How many iterations should we run before we are certain of an escape velocity?
const val ESCAPE_VELOCITY_TEST_ITERATIONS: Int = 500

data class WindowCoordinate(val x: Int, val y: Int)
data class ComplexNumber(val real: Double, val imag: Double) {
    override fun toString(): String {
        return "($real, $imag)"
    }
}

class MandelbrotView(private val window: Long) {
    init {
        GLFW.glfwSetKeyCallback(window, this::glfwKeypressCallback)
        GLFW.glfwSetMouseButtonCallback(window, this::glfwMouseClickCallback)
        setupShaders()

    }
    private var currentColorMode: Int = 0 // color modes can be changed with the keyboard
    var currentZoomLevel: Double = 1.0 //zoomed out at 100%
    var currentZoomLevelInt: Int = 1 //Just for looks
    //Unzooming proved to be problematic so until I get some better maths this works
    private val zoomStack: MutableList<Double> = mutableListOf()
    private var maxTestIterations: Int = ESCAPE_VELOCITY_TEST_ITERATIONS
    private var startHeight: Double = 2.0
    private var BOUND_TOP: Double = 1.0
    private var BOUND_BOTTOM: Double = -1.0
    private var BOUND_LEFT: Double = -2.0
    private var BOUND_RIGHT: Double = 1.0
    var currentOrthoCoordinates = ComplexNumber(-0.5, 0.0)
    private val zoomsaver = SaveZoomSequence(this)
    private var controlsLocked: Boolean = false
    private var quietMode: Boolean = false


    // Shader stuffs
    private var uloc_WINDOW_SIZE_WIDTH: Int = 0
    private var uloc_WINDOW_SIZE_HEIGHT: Int = 0
    private var uloc_CURRENT_COLOR_MODE: Int = 0
    private var uloc_ESCAPE_VELOCITY_TEST_ITERATIONS: Int = 0
    private var uloc_ORTHO_WIDTH: Int = 0
    private var uloc_ORTHO_HEIGHT: Int = 0
    private var uloc_BOUND_LEFT: Int = 0
    private var uloc_BOUND_BOTTOM: Int = 0
    private var FPMODE: Int = 0 //0 = fp32, 1 = fp64, 2 = fp128 (emulated, todo)
    private var shaderProgramFP64: Int = 0
    private var shaderProgramFP32: Int = 0

    private fun setupShaders() {
        //Lets create our shader programs and attach our shaders
        //Fragment Shaders, both 32-bit and 64-bit floating point
        val fragmentShaderFP64 = createShaderFromFile("src/mandelbrot_fp64.frag", GL20.GL_FRAGMENT_SHADER)
        val fragmentShaderFP32 = createShaderFromFile("src/mandelbrot_fp32.frag", GL20.GL_FRAGMENT_SHADER)

        //Shader program that contains all our shaders
        shaderProgramFP64 = GL20.glCreateProgram()
        shaderProgramFP32 = GL20.glCreateProgram()

        //Finally attach our compiled shaders to our shader program and use it
        GL20.glAttachShader(shaderProgramFP64, fragmentShaderFP64)
        GL20.glAttachShader(shaderProgramFP32, fragmentShaderFP32)
        //Linky linky
        GL20.glLinkProgram(shaderProgramFP64)
        GL20.glLinkProgram(shaderProgramFP32)
        //Usey Usey
        when (FPMODE) {
            0 -> GL20.glUseProgram(shaderProgramFP64)
            1 -> GL20.glUseProgram(shaderProgramFP32)
        }
        //Uniform locations only change on shader programing linking so this doesnt have to be run every loop iteration
        //We dont need to get new location references for our other shaders because we manually set the uniform locations 
        //to all be the same across the different shaders.
        uloc_WINDOW_SIZE_WIDTH = GL20.glGetUniformLocation(shaderProgramFP64, "WINDOW_SIZE_WIDTH")
        uloc_WINDOW_SIZE_HEIGHT = GL20.glGetUniformLocation(shaderProgramFP64, "WINDOW_SIZE_HEIGHT")
        uloc_CURRENT_COLOR_MODE = GL20.glGetUniformLocation(shaderProgramFP64, "CURRENT_COLOR_MODE")
        uloc_ESCAPE_VELOCITY_TEST_ITERATIONS = GL20.glGetUniformLocation(shaderProgramFP64, "ESCAPE_VELOCITY_TEST_ITERATIONS")
        uloc_ORTHO_WIDTH = GL20.glGetUniformLocation(shaderProgramFP64, "ORTHO_WIDTH")
        uloc_ORTHO_HEIGHT = GL20.glGetUniformLocation(shaderProgramFP64, "ORTHO_HEIGHT")
        uloc_BOUND_LEFT = GL20.glGetUniformLocation(shaderProgramFP64, "BOUND_LEFT")
        uloc_BOUND_BOTTOM = GL20.glGetUniformLocation(shaderProgramFP64, "BOUND_BOTTOM")
    }

    private fun updateShaderUniforms() {
        GL20.glUniform1i(uloc_WINDOW_SIZE_WIDTH, WINDOW_SIZE_WIDTH)
        GL20.glUniform1i(uloc_WINDOW_SIZE_HEIGHT, WINDOW_SIZE_HEIGHT)
        GL20.glUniform1i(uloc_CURRENT_COLOR_MODE, currentColorMode)
        GL20.glUniform1i(uloc_ESCAPE_VELOCITY_TEST_ITERATIONS, maxTestIterations)
        when (FPMODE) {
            0 -> {
                GL40.glUniform1d(uloc_ORTHO_WIDTH, getOrthoWidth())
                GL40.glUniform1d(uloc_ORTHO_HEIGHT, getOrthoHeight())
                GL40.glUniform1d(uloc_BOUND_LEFT, BOUND_LEFT)
                GL40.glUniform1d(uloc_BOUND_BOTTOM, BOUND_BOTTOM)
            }
            1 -> {
                GL20.glUniform1f(uloc_ORTHO_WIDTH, getOrthoWidth().toFloat())
                GL20.glUniform1f(uloc_ORTHO_HEIGHT, getOrthoHeight().toFloat())
                GL20.glUniform1f(uloc_BOUND_LEFT, BOUND_LEFT.toFloat())
                GL20.glUniform1f(uloc_BOUND_BOTTOM, BOUND_BOTTOM.toFloat())
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun glfwMouseClickCallback(window: Long, button: Int, action: Int, mods: Int) {
        if (action == GLFW.GLFW_PRESS)
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS && !controlsLocked) {
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
                println("Setting coordinates to: $clickOrthoCoords")
                updateView()
            }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun glfwKeypressCallback(window: Long, key: Int, scancode: Int, action: Int, mods: Int) {
        if ((action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) && !controlsLocked) {
            //needed later
            val moveAmount: Double = currentZoomLevel*startHeight/2.0*PAN_PCT_INCREMENT
            when (key) {
                GLFW.GLFW_KEY_UP -> currentOrthoCoordinates = ComplexNumber(currentOrthoCoordinates.real, currentOrthoCoordinates.imag + moveAmount)
                GLFW.GLFW_KEY_DOWN -> currentOrthoCoordinates = ComplexNumber(currentOrthoCoordinates.real, currentOrthoCoordinates.imag - moveAmount)
                GLFW.GLFW_KEY_LEFT -> currentOrthoCoordinates = ComplexNumber(currentOrthoCoordinates.real - moveAmount, currentOrthoCoordinates.imag)
                GLFW.GLFW_KEY_RIGHT -> currentOrthoCoordinates = ComplexNumber(currentOrthoCoordinates.real + moveAmount, currentOrthoCoordinates.imag)
                GLFW.GLFW_KEY_KP_ADD -> {
                    setZoomLevelFromInt(--currentZoomLevelInt)
                }
                GLFW.GLFW_KEY_KP_SUBTRACT -> {
                    setZoomLevelFromInt(++currentZoomLevelInt)
                }
                GLFW.GLFW_KEY_MINUS -> {
                    maxTestIterations -= ITERATION_INCREMENT
                    if (maxTestIterations < 0) maxTestIterations = 0
                }
                GLFW.GLFW_KEY_EQUAL ->
                    maxTestIterations += ITERATION_INCREMENT

                //Color mode keys, A and S for now
                GLFW.GLFW_KEY_S -> {
                    currentColorMode++
                    if (currentColorMode > 7) currentColorMode = 0
                }
                GLFW.GLFW_KEY_A -> {
                    currentColorMode--
                    if (currentColorMode < 0) currentColorMode = 7
                }
                //Change floating-point precision mode. only mode 0 and 1 for now
                GLFW.GLFW_KEY_KP_1 -> {
                    FPMODE = 0
                    GL20.glUseProgram(shaderProgramFP64)
                }
                GLFW.GLFW_KEY_KP_2 -> {
                    FPMODE = 1
                    GL20.glUseProgram(shaderProgramFP32)
                }
                GLFW.GLFW_KEY_1 -> currentOrthoCoordinates = ComplexNumber(0.3866831889092910, 0.5696433852559384)
                //Weird square thing @ 200 iterations and zoom ~75
                GLFW.GLFW_KEY_2 -> currentOrthoCoordinates = ComplexNumber(-1.632329465459738, 0.022135094625989362)
                //Coords from the deep zoom on wikipedia
                GLFW.GLFW_KEY_3 -> currentOrthoCoordinates = ComplexNumber(-0.743643887037158704752191506114774, 0.131825904205311970493132056385139)
                //Cover of August 1985 issue of Scientific American
                GLFW.GLFW_KEY_4 -> currentOrthoCoordinates = ComplexNumber(-0.909,0.275)
                GLFW.GLFW_KEY_5 -> currentOrthoCoordinates = ComplexNumber(0.001643721971153, -0.822467633298876)
                GLFW.GLFW_KEY_6 -> currentOrthoCoordinates = ComplexNumber(-1.2032239372416502, 0.16503554579069707)
                GLFW.GLFW_KEY_7 -> currentOrthoCoordinates = ComplexNumber(-0.7489804117521476, -0.050907824616219184)
                GLFW.GLFW_KEY_KP_0 -> resetAll()
                GLFW.GLFW_KEY_Z -> zoomsaver.savePngSequence()
                else -> return
            }
            updateView()
        }
    }

    private fun resetAll() {
        currentColorMode = 0
        maxTestIterations = ESCAPE_VELOCITY_TEST_ITERATIONS
        BOUND_TOP = 1.0
        BOUND_LEFT = -2.0
        currentZoomLevel = 1.0
        currentZoomLevelInt = 1
        zoomStack.clear()
        currentOrthoCoordinates = ComplexNumber(-0.5, 0.0)
        updateView()
    }

    fun updateView() {
        val height: Double = getOrthoHeight()
        val width: Double = height*3.0/2.0
        BOUND_LEFT = currentOrthoCoordinates.real-width/2
        BOUND_TOP = currentOrthoCoordinates.imag+height/2
        BOUND_BOTTOM = BOUND_TOP - height
        BOUND_RIGHT = BOUND_LEFT + width
        redrawView()
    }

    fun setZoomLevelFromInt(zoomLevelInt: Int) {
        currentZoomLevel = pow(1.0-ZOOM_INCREMENT, (zoomLevelInt-1).toDouble())
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

    private fun updateTitle(s: String = "") {
        val fpmode: String = when (FPMODE) {
            1 -> "FP32"
            else -> "FP64"
        }
        GLFW.glfwSetWindowTitle(window, "Mandelbrot Set ($fpmode) :: $currentOrthoCoordinates :: Zoom Level: $currentZoomLevelInt :: Max iterations: $maxTestIterations :: Color Mode: $currentColorMode $s")
    }

    private fun redrawView() {
        val starttime = System.currentTimeMillis()
        if (!quietMode) println("Generating simple Mandelbrot set at Coords: $currentOrthoCoordinates  Zoomlevel: $currentZoomLevelInt  Max iterations: $maxTestIterations  ::  Color Mode: $currentColorMode  (this could take a while)...")
        updateTitle(":: Generating...")
        updateShaderUniforms()
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glVertex2f(-1.0f, 1.0f)
        GL11.glVertex2f(1.0f, 1.0f)
        GL11.glVertex2f(1.0f, -1.0f)
        GL11.glVertex2f(-1.0f, -1.0f)
        GL11.glEnd()
        //Using glFlush() instead of swapBuffers because there is a significant performance penalty when using swapBuffers
        //with a slow fragment shader. As I understand it, swapBuffers waits until the preceding frame has drawn before swapping.
        //Switching to glFlush() eliminates this overhead entirely. This performance issue only occurred when many changes piled up.
        GL11.glFlush()
        //Need this call otherwise our timer returns 0
        glFinish()
        if (!quietMode) println("Done! Took ${(System.currentTimeMillis() - starttime)} milliseconds.")
        updateTitle()
    }

    fun lockForZoom() {
        controlsLocked = true
        quietMode = true
    }

    fun unlockAfterZoom() {
        controlsLocked = false
        quietMode = false
    }
}

fun main(args: Array<String>) {
    window = glinit(WINDOW_SIZE_WIDTH, WINDOW_SIZE_HEIGHT, "Mandelbrot Set")
    val viewControl = MandelbrotView(window)
    viewControl.updateView()
    //and wait for any keyboard stuffs now
    while (!GLFW.glfwWindowShouldClose(window)) {
        GLFW.glfwPollEvents()
        Thread.sleep(100)
    }
}
