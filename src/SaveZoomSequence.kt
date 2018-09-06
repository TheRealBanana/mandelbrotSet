import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

//path to save the image sequence in. If it doesn't exist, we'll crash.
const val SAVEPATH = "."

//These special words will be replaced with actual data
//You must include the frame number, otherwise each picture will overwrite the last.
// Frame number = %framenum%
// Coordinates = %coords%
// Max Iterations = %itermax%
//
const val FILENAME_TEMPLATE = "Zoomsequence-%coords%-%itermax%_%framenum%.png"

//Controls the final zoom level and number of frames generated
const val FINAL_ZOOM_LEVEL: Int = 80
const val FRAME_COUNT: Int = FINAL_ZOOM_LEVEL*3 //3 frames per zoom level is sufficient

class SaveZoomSequence(private val mandelhandle: MandelbrotView) {
    fun savePngSequence(finalZoomLevelInt: Int = FINAL_ZOOM_LEVEL, numFrames: Int = FRAME_COUNT) {
        println("Launching zoom program...")
        mandelhandle.lockForZoom()
        val finalZoomLevel: Double = Math.pow(1.0 - ZOOM_INCREMENT, finalZoomLevelInt - 1.0)
        //We want numFrames/finalZoomLevelInt steps between each actual zoom level.
        val startzoomincrement = ZOOM_INCREMENT
        //We need to modify our zoom increment to be ZOOM_INCREMENT/numFrames/finalZoomLevelInt
        ZOOM_INCREMENT /= numFrames.toFloat()/finalZoomLevelInt.toFloat()
        var z = 1
        mandelhandle.setZoomLevelFromInt(z)
        while (mandelhandle.currentZoomLevel > finalZoomLevel && !GLFW.glfwWindowShouldClose(window)) {
            mandelhandle.currentZoomLevelInt = z
            mandelhandle.setZoomLevelFromInt(z)
            mandelhandle.updateView()
            takeScreenshot(z)
            z++
            GLFW.glfwPollEvents() //Need to do this otherwise the screen freezes and doesnt update/accept keyboard input
        }
        ZOOM_INCREMENT = startzoomincrement
        mandelhandle.unlockAfterZoom()
        println("Zoom program finished! Output frames are located in $SAVEPATH")

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
        var savefilename = FILENAME_TEMPLATE
        savefilename = savefilename.replace("%framenum%", frameNumber.toString())
        savefilename = savefilename.replace("%coords%", mandelhandle.currentOrthoCoordinates.toString())
        savefilename = savefilename.replace("%itermax%", ESCAPE_VELOCITY_TEST_ITERATIONS.toString())
        val file = File(SAVEPATH, savefilename) // The file to save to.
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
            ImageIO.write(image, format, file)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}