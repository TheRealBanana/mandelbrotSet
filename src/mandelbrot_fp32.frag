#version 430
#extension GL_ARB_gpu_shader_fp64 : require

layout(location = 0) uniform int WINDOW_SIZE_WIDTH;
layout(location = 1) uniform int WINDOW_SIZE_HEIGHT;
layout(location = 2) uniform int CURRENT_COLOR_MODE;
layout(location = 3) uniform int ESCAPE_VELOCITY_TEST_ITERATIONS;
layout(location = 4) uniform float ORTHO_WIDTH;
layout(location = 5) uniform float ORTHO_HEIGHT;
layout(location = 6) uniform float BOUND_LEFT;
layout(location = 7) uniform float BOUND_BOTTOM;

layout(location = 0) out vec4 fragColor;

//Borrowed from http://lolengine.net/blog/2013/07/27/rgb-to-hsv-in-glsl
vec3 hsv2rgb(vec3 c)
{
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}
//Borrowed from the map() function in the open-source Processing Core development environment.
//Using this because the HSV color range both starts and ends at pure red. Id rather it begin
//around purple and end at red, that way there is a progression from "cool" to "hot" colors.
float map(float value, float min1, float max1, float min2, float max2) {
  return min2 + (value - min1) * (max2 - min2) / (max1 - min1);
}
vec2 complexAdd(vec2 c1, vec2 c2) {
    return vec2(c1.x+c2.x, c1.y+c2.y);
}
vec2 complexMulti(vec2 c1, vec2 c2) {
    float imag = (c1.x * c2.y) + (c1.y * c2.x);
    float real = (c1.x * c2.x) + (c1.y * c2.y * -1.0);
    return vec2(real,imag);
}

float findEscapeVelocity(vec2 c) {
    vec2 z = vec2(0.0, 0.0);
    int iter = 1;
    float zRealSquared = z.x*z.x;
    float zImagSquared = z.y*z.y;
    while (zRealSquared + zImagSquared < 4.0 && iter < ESCAPE_VELOCITY_TEST_ITERATIONS) {
        //Moved out of functions to increase speed... but it didnt.
        //I think this means the GLSL compiler was pretty smart and made these optimizations for us.
        // Much thanks to https://randomascii.wordpress.com/2011/08/13/faster-fractals-through-algebra
        //
        //Z^2+c
        z.y = (z.x * z.y);
        z.y += z.y;
        z.y += c.y;
        z.x = (zRealSquared) - zImagSquared + c.x;
        iter++;
        zRealSquared = z.x*z.x;
        zImagSquared = z.y*z.y;
    }
    if (zRealSquared + zImagSquared >= 4.0) {
        return float(iter)/float(ESCAPE_VELOCITY_TEST_ITERATIONS);
    }
    return 0.0;
}
vec2 getOrthoCoordsFromWindowCoords(float x, float y) {
    float orthoX = (x/float(WINDOW_SIZE_WIDTH) * ORTHO_WIDTH) + BOUND_LEFT;
    float orthoY = (y/float(WINDOW_SIZE_HEIGHT) * ORTHO_HEIGHT) + BOUND_BOTTOM;
    return vec2(orthoX, orthoY);
}
vec4 getColorFromVelocity(float v) {
    if (v == 0.0) return vec4(0.0,0.0,0.0,1.0);
    vec4 retcolor = vec4(v,0.0,0.0,1.0);
    if      (CURRENT_COLOR_MODE == 0){retcolor = vec4(hsv2rgb(vec3(1-map(v, 0.0, 1.0, 0.25, 1.0), 1.0, 1.0)), 1.0); }
    else if (CURRENT_COLOR_MODE == 1){retcolor = vec4(hsv2rgb(vec3(map(v, 0.0, 1.0, 0.0, 0.8), 1.0, 1.0)), 1.0); }
    else if (CURRENT_COLOR_MODE == 2) retcolor = vec4(v,0.0,0.0,1.0);
    else if (CURRENT_COLOR_MODE == 3) retcolor = vec4(0.0,v,0.0,1.0);
    else if (CURRENT_COLOR_MODE == 4) retcolor = vec4(0.0,0.0,v,1.0);
    else if (CURRENT_COLOR_MODE == 5) retcolor = vec4(v,0.0,v,1.0);
    else if (CURRENT_COLOR_MODE == 6) retcolor = vec4(v,v,1.0,1.0);
    else if (CURRENT_COLOR_MODE == 7) retcolor = vec4(1.0,v,v,1.0);
    return retcolor;
}
void main() {
    vec2 orthoCoords = getOrthoCoordsFromWindowCoords(float(gl_FragCoord.x), float(gl_FragCoord.y));
    float normalizedVelocity = findEscapeVelocity(orthoCoords);
	fragColor = getColorFromVelocity(normalizedVelocity);
}