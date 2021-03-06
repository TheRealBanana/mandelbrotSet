import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.glfw.GLFW
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL
import java.nio.IntBuffer
import kotlin.math.max
import kotlin.math.pow
import kotlin.system.exitProcess


//This gets overridden at runtime
var GL33MODE: Boolean = false

//For performance testing. Runs everything as fast as it can and sets the zoom level
//to 20 at the origin coords. This is the worst case scenario for performance.
const val PERFORMANCE_TESTING: Boolean = false

//Zoom/pan increment stuffs
var ZOOM_INCREMENT = 0.3 //increase by X%
const val PAN_PCT_INCREMENT = 0.25 //Move view by X%
const val ITERATION_INCREMENT = 20

//GL STUFFS
//Locking aspect ratio to 3:2

const val WINDOW_SIZE_HEIGHT = 1000
const val WINDOW_SIZE_WIDTH = (WINDOW_SIZE_HEIGHT/(2.0/3.0)).toInt()

var window: Long = NULL

//How many iterations should we run before we are certain of an escape velocity?
const val ESCAPE_VELOCITY_TEST_ITERATIONS: Int = 500

data class WindowCoordinate(val x: Int, val y: Int)
data class ComplexNumber(var real: Double, var imag: Double) {
    override fun toString(): String {
        return "($real, $imag)"
    }
}

class MandelbrotView(private val window: Long) {
    private var ASPECT_RATIO: Double = 3.0/2.0 // Will change for bifurcation mode
    private var currentColorMode: Int = 0 // color modes can be changed with the keyboard
    var currentZoomLevel: Double = 1.0 //zoomed out at 100%
    var currentZoomLevelInt: Int = 1 //Just for looks
    var maxTestIterations: Int = ESCAPE_VELOCITY_TEST_ITERATIONS
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
    private val uniformNames: Array<String> = arrayOf(
            "WINDOW_SIZE_WIDTH",
            "WINDOW_SIZE_HEIGHT",
            "CURRENT_COLOR_MODE",
            "ESCAPE_VELOCITY_TEST_ITERATIONS",
            "ORTHO_WIDTH",
            "ORTHO_HEIGHT",
            "BOUND_LEFT",
            "BOUND_BOTTOM"
    )
    private lateinit var uniformIndices: IntBuffer
    private lateinit var uniformOffsets: IntBuffer
    private var uniformBlockIndex = -1
    private var finalBufferSize = -1
    private val uniformBufferObject = GL15.glGenBuffers()
    private var FPMODE: Int = 0 //0 = fp32, 1 = fp64, 2 = fp128 (emulated, todo)
    private var shaderProgramFP64: Int = 0
    private var shaderProgramFP32: Int = 0
    private var BIFMODE: Boolean = false
    init {
        GLFW.glfwSetKeyCallback(window, this::glfwKeypressCallback)
        GLFW.glfwSetMouseButtonCallback(window, this::glfwMouseClickCallback)
        //Check for fp64 support and fall back to GL33 mode if we dont have it.
        if (!GL.getCapabilities().GL_ARB_gpu_shader_fp64) {
            println("Your GPU doesn't support GL_ARB_gpu_shader_fp64. Attempting to fall back to 32-bit mode.")
            GL33MODE = true
            FPMODE = 0
        } else {
            println("Using fp64 extension.")
            FPMODE = 1
        }
        if (!GL.getCapabilities().OpenGL33) {
            println("This program requires a GPU with support for OpenGL v3.3 or later.\nExiting...")
            exitProcess(1)
        }
        setupShaders()
        //Performance testing
        if (PERFORMANCE_TESTING) setZoomLevelFromInt(20)
    }

    private fun setupShaders() {
        //Lets create our shader programs and attach our shaders
        //32-bit Fragment Shader floating point (64-bit below)
        val fragmentShaderFP32 = createShaderFromFile("src/mandelbrot_fp32.frag", GL20.GL_FRAGMENT_SHADER)

        //Shader program that contains all our shaders
        shaderProgramFP32 = GL20.glCreateProgram()

        //Finally attach our compiled shaders to our shader program and use it
        GL20.glAttachShader(shaderProgramFP32, fragmentShaderFP32)
        //Linky linky
        GL20.glLinkProgram(shaderProgramFP32)

        //Check if we have OpenGL44 and use it if we do
        if (!GL33MODE) {
            val fragmentShaderFP64 = createShaderFromFile("src/mandelbrot_fp64.frag", GL20.GL_FRAGMENT_SHADER)
            shaderProgramFP64 = GL20.glCreateProgram()
            GL20.glAttachShader(shaderProgramFP64, fragmentShaderFP64)
            GL20.glLinkProgram(shaderProgramFP64)
            changeShaderProgram(shaderProgramFP64)
        } else {
            changeShaderProgram(shaderProgramFP32)
        }
    }
    //Split off this function because we have to change the offsets/indices every time anyway.
    private fun changeShaderProgram(shaderprogref: Int) {
        //Usey Use
        GL20.glUseProgram(shaderprogref)
        //Set up the uniform locations so we can update the data later
        //should test if the uniform indices actually change between fp32 and fp64 shaders.
        uniformIndices = BufferUtils.createIntBuffer(uniformNames.size)
        uniformOffsets = BufferUtils.createIntBuffer(uniformNames.size)
        GL31.glGetUniformIndices(shaderprogref, uniformNames, uniformIndices)
        GL31.glGetActiveUniformsiv(shaderprogref, uniformIndices, GL31.GL_UNIFORM_OFFSET, uniformOffsets)
        //Now we have the indices for each of our uniforms and their offsets.
        //We can use this information to correctly pack our final buffer
        //figure out the index and size of our uniform block
        //The uniform block index may not change between fp32 and fp64, something to test
        uniformBlockIndex = GL31.glGetUniformBlockIndex(shaderprogref, "PARAMS")
        finalBufferSize = GL31.glGetActiveUniformBlocki(shaderprogref, uniformBlockIndex, GL31.GL_UNIFORM_BLOCK_DATA_SIZE)
        //Bind our uniformBufferObject to the GL_UNIFORM_BUFFER target
        //and then bind our buffer to the index of our uniform block
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, uniformBufferObject)
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, uniformBlockIndex, uniformBufferObject)
        //To create the initial buffer we pass finalBufferSize instead of the data.
        GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, finalBufferSize.toLong(), GL15.GL_DYNAMIC_DRAW)
    }

    private fun updateShaderUniforms() {
        //Starting to get the hang of this whole buffer thing, although I'd much prefer c-style pointers
        val finalBuffer = BufferUtils.createByteBuffer(finalBufferSize)

        //going in the same order as uniformNames
        finalBuffer.putInt(uniformOffsets.get(uniformNames.indexOf("WINDOW_SIZE_WIDTH")), WINDOW_SIZE_WIDTH)
        finalBuffer.putInt(uniformOffsets.get(uniformNames.indexOf("WINDOW_SIZE_HEIGHT")), WINDOW_SIZE_HEIGHT)
        finalBuffer.putInt(uniformOffsets.get(uniformNames.indexOf("CURRENT_COLOR_MODE")), currentColorMode)
        finalBuffer.putInt(uniformOffsets.get(uniformNames.indexOf("ESCAPE_VELOCITY_TEST_ITERATIONS")), maxTestIterations)
        when (FPMODE) {
            0 -> {
                finalBuffer.putFloat(uniformOffsets.get(uniformNames.indexOf("ORTHO_WIDTH")), getOrthoWidth().toFloat())
                finalBuffer.putFloat(uniformOffsets.get(uniformNames.indexOf("ORTHO_HEIGHT")), getOrthoHeight().toFloat())
                finalBuffer.putFloat(uniformOffsets.get(uniformNames.indexOf("BOUND_LEFT")), BOUND_LEFT.toFloat())
                finalBuffer.putFloat(uniformOffsets.get(uniformNames.indexOf("BOUND_BOTTOM")), BOUND_BOTTOM.toFloat())
            }
            1 -> {
                if (!GL33MODE) {
                    finalBuffer.putDouble(uniformOffsets.get(uniformNames.indexOf("ORTHO_WIDTH")), getOrthoWidth())
                    finalBuffer.putDouble(uniformOffsets.get(uniformNames.indexOf("ORTHO_HEIGHT")), getOrthoHeight())
                    finalBuffer.putDouble(uniformOffsets.get(uniformNames.indexOf("BOUND_LEFT")), BOUND_LEFT)
                    finalBuffer.putDouble(uniformOffsets.get(uniformNames.indexOf("BOUND_BOTTOM")), BOUND_BOTTOM)
                }
            }
        }
        //Since we are resetting everything, we can use 0 for our offset.
        GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0, finalBuffer)
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
                //instead of the OpenGL standard of the bottom left. Easily dealt with however
                val ycoord = ybuff.get(0).toInt()
                val xcoord = xbuff.get(0).toInt()
                val reversedYCoord: Int = WINDOW_SIZE_HEIGHT - ycoord
                val clickOrthoCoords: ComplexNumber = getOrthoCoordsFromWindowCoords(WindowCoordinate(xcoord, reversedYCoord))
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
                GLFW.GLFW_KEY_UP -> currentOrthoCoordinates.imag += moveAmount
                GLFW.GLFW_KEY_DOWN -> currentOrthoCoordinates.imag -= moveAmount
                GLFW.GLFW_KEY_LEFT -> currentOrthoCoordinates.real -= moveAmount
                GLFW.GLFW_KEY_RIGHT -> currentOrthoCoordinates.real += moveAmount
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
                    changeShaderProgram(shaderProgramFP32)
                }
                GLFW.GLFW_KEY_KP_2 -> {
                    if (!GL33MODE) {
                        FPMODE = 1
                        changeShaderProgram(shaderProgramFP64)
                    }
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
                GLFW.GLFW_KEY_8 -> currentOrthoCoordinates = ComplexNumber(-1.1159543391092177, 0.22496904622127784)
                //Color zooms
                GLFW.GLFW_KEY_9 -> currentOrthoCoordinates = ComplexNumber(0.25438986506872713, 0.00046726730914516275)
                GLFW.GLFW_KEY_Q -> currentOrthoCoordinates = ComplexNumber(-0.7107128536934894, 0.28928443357282263)
                //Center of spider-web thingy from the bifurcation diagram (-1.5436890198239954, -0.8392866692155266)?
                GLFW.GLFW_KEY_0 -> currentOrthoCoordinates = ComplexNumber(-1.5436890198239954, 0.0)
                GLFW.GLFW_KEY_KP_0 -> resetAll()
                GLFW.GLFW_KEY_Z -> zoomsaver.saveMagnificationZoom(save=mods) // Magnification Zoom
                GLFW.GLFW_KEY_X -> zoomsaver.iterationZoom(save=mods) // Color Zoom
                GLFW.GLFW_KEY_B -> {
                    resetAll()
                    println("Bifurcation Diagram Mode Starting")
                    //Standard values that need to be changed before we can display correctly
                    BIFMODE = true
                    GL20.glUseProgram(0) //Unload current shader
                    ASPECT_RATIO = 0.5625
                    startHeight = 4.0
                    maxTestIterations = 600
                    BOUND_LEFT = -2.0
                    BOUND_RIGHT = 0.25
                    BOUND_BOTTOM = -2.0
                    BOUND_TOP = 2.0
                    currentOrthoCoordinates = ComplexNumber(((BOUND_RIGHT + BOUND_LEFT)/2.0), ((BOUND_TOP + BOUND_BOTTOM)/2.0))
                    updateView()
                }
                else -> return
            }
            updateView()
        } else if ((action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) && controlsLocked) {
            when (key) {
                GLFW.GLFW_KEY_ESCAPE -> zoomsaver.cancelZoom()
            }
        }
    }

    private fun resetAll() {
        resetDisplay()
        currentColorMode = 0
        maxTestIterations = ESCAPE_VELOCITY_TEST_ITERATIONS
        ASPECT_RATIO = 3.0/2.0
        BOUND_TOP = 1.0
        BOUND_LEFT = -2.0
        startHeight = 2.0
        currentZoomLevel = 1.0
        currentZoomLevelInt = 1
        currentOrthoCoordinates = ComplexNumber(-0.5, 0.0)
        BIFMODE = false
        if (!GL33MODE) {
            FPMODE = 1
            changeShaderProgram(shaderProgramFP64)
        } else {
            FPMODE = 0
            changeShaderProgram(shaderProgramFP32)
        }
        updateView()
    }

    fun updateView() {
        val height: Double = getOrthoHeight()
        val width: Double = height * ASPECT_RATIO
        BOUND_LEFT = currentOrthoCoordinates.real - width / 2
        BOUND_TOP = currentOrthoCoordinates.imag + height / 2
        BOUND_BOTTOM = BOUND_TOP - height
        BOUND_RIGHT = BOUND_LEFT + width
        if (!BIFMODE) redrawView()
        else generateBifurcationDiagram()
        updateTitle()

    }

    fun setZoomLevelFromInt(zoomLevelInt: Int) {
        currentZoomLevel = (1.0 - ZOOM_INCREMENT).pow((zoomLevelInt - 1).toDouble())
        currentZoomLevelInt = zoomLevelInt
    }

    private fun getOrthoHeight(): Double { return currentZoomLevel * startHeight }
    private fun getOrthoWidth(): Double { return getOrthoHeight()*ASPECT_RATIO}
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
            0 -> "FP32"
            else -> "FP64"
        }
        val genmode: String = when(BIFMODE) {
            true -> "Bifurcation Diagram"
            false -> "Mandelbrot Set ($fpmode)"
        }
        GLFW.glfwSetWindowTitle(window, "$genmode :: $currentOrthoCoordinates :: Zoom Level: $currentZoomLevelInt :: Max iterations: $maxTestIterations :: Color Mode: $currentColorMode $s")
    }

    private fun redrawView() {
        val starttime = System.currentTimeMillis()
        if (!quietMode) println("Generating simple Mandelbrot set at Coords: $currentOrthoCoordinates  Zoomlevel: $currentZoomLevelInt  Max iterations: $maxTestIterations  ::  Color Mode: $currentColorMode  (this could take a while)...")
        updateTitle(":: Generating...")
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
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
        GL11.glFinish()
        if (!quietMode) println("Done! Took ${(System.currentTimeMillis() - starttime)} milliseconds.")
    }

    fun lockForZoom() {
        controlsLocked = true
        quietMode = true
    }

    fun unlockAfterZoom() {
        controlsLocked = false
        quietMode = false
    }

    // Lots of help from this page:
    // https://users.math.yale.edu/public_html/People/frame/Fractals/MandelSet/MandelDef/LogisticMand/LogisticMand.html
    //
    // Didn't think a shader would be great for this but I think it would work with the current shaders.
    // We already calculated these values in the shader anyway, we just have to send the right ones back.
    // Or we just switch modes inside the shader and use that data. The problem is you can't set other pixels
    // besides the one you are working on inside the frag shader (I think). Will have to test. .
    private fun generateBifurcationDiagram() {
        GL11.glLoadIdentity()
        GL11.glOrtho(BOUND_LEFT, BOUND_RIGHT, BOUND_BOTTOM, BOUND_TOP, -1.0, 1.0)
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
        GL11.glPointSize(1.0f)
        GL11.glColor3f(1.0f, 0.0f, 0.0f)
        GL11.glBegin(GL11.GL_POINTS)

        // Even though using the horizontal pixel count gives us per-pixel accuracy, we are still missing data between
        // the pixels. This is easily seen in spots where a single value splits into two; the vertical sections of the
        // lines are sparsely populated. We need to figure out anti-aliasing or maybe we draw using the vertical axis
        // instead which should give more accuracy (I think). Probably will also introduce other artifacts though.
        for (i in 0..WINDOW_SIZE_WIDTH) {
            val c = ((i.toDouble()/WINDOW_SIZE_WIDTH)*getOrthoWidth()) + BOUND_LEFT
            var y = 0.0
            for (j in 0..maxTestIterations) {
                y = y.pow(2.0) + c
                // Only draw the last 1000 iterations and never any less than 5000 iterations
                if (j > max(500, maxTestIterations-1000)) GL11.glVertex2d(c, y)
            }
        }
        GL11.glEnd()
        GL11.glFinish()
        GL11.glFlush()
    }

}

fun main(args: Array<String>) {
    window = glinit(WINDOW_SIZE_WIDTH, WINDOW_SIZE_HEIGHT, "Mandelbrot Set")
    val viewControl = MandelbrotView(window)
    viewControl.updateView()
    //and wait for any keyboard stuffs now
    while (!GLFW.glfwWindowShouldClose(window)) {
        GLFW.glfwPollEvents()
        if (!PERFORMANCE_TESTING) Thread.sleep(100)
        else viewControl.updateView()
    }
}



