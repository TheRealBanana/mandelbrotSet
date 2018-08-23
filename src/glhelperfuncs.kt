package org.kylesplace.kotlin.GLhelperfunctions

import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import org.lwjgl.system.MemoryUtil
import java.io.File

fun createShaderFromFile(shaderFilePath: String, shaderType: Int): Int {
    val shaderFileText = File(shaderFilePath).readText(Charsets.UTF_8)
    val shaderHandle = GL20.glCreateShader(shaderType)
    GL20.glShaderSource(shaderHandle, shaderFileText)
    GL20.glCompileShader(shaderHandle)
    //Check for compilation errors so we dont just stare at a white screen when it breaks
    if (GL20.glGetShaderi(shaderHandle, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
        val compileerror = GL20.glGetShaderInfoLog(shaderHandle, 1024)
        println("Shader compilation error for shader file $shaderFilePath")
        println(compileerror)
        System.exit(1)
    }

    return shaderHandle
}

fun glinit(windowSizeW: Int, windowSizeH: Int, windowTitle: String = "Untitled Window"): Long {
    if ( !GLFW.glfwInit()) {
        throw Exception("Failed to initialize GLFW.")
    }
    GLFW.glfwDefaultWindowHints()
    //Do not allow resize
    GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE)
    GLFW.glfwWindowHint(GLFW.GLFW_DOUBLEBUFFER, GLFW.GLFW_TRUE)
    val window = GLFW.glfwCreateWindow(windowSizeW, windowSizeH, windowTitle, 0, 0)
    if (window == MemoryUtil.NULL) {
        throw Exception("Failed to initialize window.")
    }
    GLFW.glfwMakeContextCurrent(window)
    // GL configuration comes AFTER we make the window our current context, otherwise errors
    GL.createCapabilities()
    GL11.glClearColor(1.0f, 1.0f, 1.0f, 1.0f) //white background
    GL11.glViewport(0, 0, windowSizeW, windowSizeH)
    GL11.glMatrixMode(GL11.GL_PROJECTION)
    GL11.glOrtho(-1.0, 1.0, -1.0, 1.0, -1.0, 1.0)
    GLFW.glfwShowWindow(window)
    
    return window
}