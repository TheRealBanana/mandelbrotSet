import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.lang.Math.pow
import javax.imageio.ImageIO
import kotlin.math.roundToInt

//path to save the image sequence in. If it doesn't exist, we'll crash.
const val SAVEPATH = "./zoomsequence/%coords% - %itermax% - %zoomlevel%"

//These special words will be replaced with actual data
//You must include the frame number, otherwise each picture will overwrite the last.
// Frame number = %framenum%
// Coordinates = %coords%
// Max Iterations = %itermax%
//
const val FILENAME_TEMPLATE = "Zoomsequence-%framenum%-%itermax%.png"

//Controls the final zoom level and number of frames generated
const val FINAL_ZOOM_LEVEL: Int = 80
const val FRAME_COUNT: Int = FINAL_ZOOM_LEVEL*3 //3 frames per zoom level is sufficient

class SaveZoomSequence(private val mandelhandle: MandelbrotView) {
    private var savefilename: String = FILENAME_TEMPLATE
    private var savepathname: String = SAVEPATH

    fun updateStrings() {
        savefilename = FILENAME_TEMPLATE
        savefilename = savefilename.replace("%coords%", mandelhandle.currentOrthoCoordinates.toString())
        savefilename = savefilename.replace("%itermax%", mandelhandle.maxTestIterations.toString())
        savefilename = savefilename.replace("%zoomlevel%", mandelhandle.currentZoomLevelInt.toString())
        savepathname = savepathname.replace("%coords%", mandelhandle.currentOrthoCoordinates.toString())
        savepathname = savepathname.replace("%itermax%", mandelhandle.maxTestIterations.toString())
        savepathname = savepathname.replace("%zoomlevel%", mandelhandle.currentZoomLevelInt.toString())
        //Check our folder exists first
        if (!File(savepathname).exists()) {
            try {
                File(savepathname).mkdirs()
            } catch (e: Exception){
                println("Error creating zoom save dir, saving to current dir instead")
                savepathname = "."
            }
        }
    }

    fun savePngSequence(mode: Int, finalZoomLevelInt: Int = FINAL_ZOOM_LEVEL, numFrames: Int = FRAME_COUNT) {
        println("Launching zoom program...")
        mandelhandle.lockForZoom()
        val starttime = System.currentTimeMillis()
        when (mode) {
            1 -> {
                val finalZoomLevel: Double = Math.pow(1.0 - ZOOM_INCREMENT, finalZoomLevelInt - 1.0)
                //We want numFrames/finalZoomLevelInt steps between each actual zoom level.
                val startzoomincrement = ZOOM_INCREMENT
                //We need to modify our zoom increment to be ZOOM_INCREMENT/numFrames/finalZoomLevelInt
                ZOOM_INCREMENT /= numFrames.toFloat() / finalZoomLevelInt.toFloat()
                var z = 1
                mandelhandle.setZoomLevelFromInt(z)
                updateStrings()
                while (mandelhandle.currentZoomLevel > finalZoomLevel && !GLFW.glfwWindowShouldClose(window)) {
                    GLFW.glfwPollEvents()
                    mandelhandle.currentZoomLevelInt = z
                    mandelhandle.setZoomLevelFromInt(z)
                    mandelhandle.updateView()
                    takeScreenshot(z)
                    z++
                    GLFW.glfwPollEvents() //Need to do this otherwise the screen freezes and doesnt update/accept keyboard input
                }
                ZOOM_INCREMENT = startzoomincrement
            }
            2 -> {
                //Initially this mode was a linear progression but I found a logarithmic to be far better suited
                mandelhandle.maxTestIterations = mandelhandle.maxTestIterations * 100
                val finalIterLevel = mandelhandle.maxTestIterations
                //val iterstep = 0
                val maxframes = 450 // 30 seconds @ 15fps
                //base is root(maxframes,maxIters)
                val base = pow(finalIterLevel.toDouble(), 1.0/maxframes.toDouble())
                savepathname = SAVEPATH
                updateStrings()
                println("Starting color zoom to ${finalIterLevel}")
                mandelhandle.maxTestIterations = 1
                var z = 1
                while (!GLFW.glfwWindowShouldClose(window) && z < maxframes+1) {
                    GLFW.glfwPollEvents()
                    mandelhandle.updateView()
                    updateStrings()
                    takeScreenshot(z)
                    mandelhandle.maxTestIterations = (pow(base, z.toDouble())).roundToInt()
                    z += 1
                }

            }
        }
        mandelhandle.unlockAfterZoom()
        println("Zoom program finished in ${(System.currentTimeMillis() - starttime).toDouble()/1000.0}s! Output frames are located in $savepathname")

    }

    //From http://wiki.lwjgl.org/wiki/Taking_Screen_Shots.html
    //Auto converted to kotlin by IntelliJ with minor adjustments
    private fun takeScreenshot(frameNumber: Int) {
        GL11.glReadBuffer(GL11.GL_FRONT)
        val width = WINDOW_SIZE_WIDTH
        val height = WINDOW_SIZE_HEIGHT
        val bpp = 4 // Assuming a 32-bit display with a byte each for red, green, blue, and alpha.
        val buffer = BufferUtils.createByteBuffer(width * height * bpp)
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer)
        //update the only dynamic data in our filename
        val curname = savefilename.replace("%framenum%", frameNumber.toString())
        val savefile = File(savepathname, curname) // The file to save to.
        val format = "png" // Example: "PNG" or "JPG"
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val i = (x + width * y) * bpp
                val r = buffer.get(i).toInt() and 0xFF
                val g = buffer.get(i + 1).toInt() and 0xFF
                val b = buffer.get(i + 2).toInt() and 0xFF
                //val rgbBytes: Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b // The way the docs say to do this
                //Instead of all the bitwise operations (which are kinda ugly) lets do it a nicer way
                //I'm sure its slower but Its nicer to look at and makes a whole lot more sense for anyone unfamiliar with the code.
                val rgbBytes = Color(r, g, b).rgb
                image.setRGB(x, height - (y + 1), rgbBytes)
            }
        }
        try {
            ImageIO.write(image, format, savefile)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}