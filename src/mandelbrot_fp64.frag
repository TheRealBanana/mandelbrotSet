#version 400
#extension GL_ARB_gpu_shader_fp64 : require

uniform int WINDOW_SIZE_WIDTH;
uniform int WINDOW_SIZE_HEIGHT;
uniform int CURRENT_COLOR_MODE;
uniform int ESCAPE_VELOCITY_TEST_ITERATIONS;
uniform double ORTHO_WIDTH;
uniform double ORTHO_HEIGHT;
uniform double BOUND_LEFT;
uniform double BOUND_BOTTOM;

layout(location = 0) out vec4 fragColor;

dvec2 complexAdd(dvec2 c1, dvec2 c2) {
    return dvec2(c1.x+c2.x, c1.y+c2.y);
}
dvec2 complexSubtract(vec2 c1, dvec2 c2) {
    return dvec2(c1.x-c2.x, c1.y-c2.y);
}
dvec2 complexMulti(dvec2 c1, dvec2 c2) {
    double imag = (c1.x * c2.y) + (c1.y * c2.x);
    double real = (c1.x * c2.x) + (c1.y * c2.y * -1.0);
    return dvec2(real,imag);
}
double complexMagnitude(dvec2 c) {
    if (c.x == 0.0 && c.y == 0.0) {
        return 0.0;
    } else {
        return sqrt(c.x*c.x + c.y*c.y);
    }
}
double findEscapeVelocity(dvec2 c) {
    dvec2 z = dvec2(0.0, 0.0);
    int iter = 1;
    while (iter < ESCAPE_VELOCITY_TEST_ITERATIONS) {
        if (complexMagnitude(z) > 2.0) {
            return double(iter)/double(ESCAPE_VELOCITY_TEST_ITERATIONS);
        }
        z = complexMulti(z, z);
        z = complexAdd(z, c);
        iter++;
    }
    return 0.0;
}
dvec2 getOrthoCoordsFromWindowCoords(double x, double y) {
    double orthoX = (x/double(WINDOW_SIZE_WIDTH) * ORTHO_WIDTH) + BOUND_LEFT;
    double orthoY = (y/double(WINDOW_SIZE_HEIGHT) * ORTHO_HEIGHT) + BOUND_BOTTOM;
    return dvec2(orthoX, orthoY);
}
vec4 getColorFromVelocity(double v) {
    vec4 retcolor = vec4(v,0.0,0.0,1.0);
    if (CURRENT_COLOR_MODE == 0) retcolor = vec4(v,0.0,0.0,1.0);
    else if (CURRENT_COLOR_MODE == 1) retcolor = vec4(0.0,v,0.0,1.0);
    else if (CURRENT_COLOR_MODE == 2) retcolor = vec4(0.0,0.0,v,1.0);
    else if (CURRENT_COLOR_MODE == 3) retcolor = vec4(v,0.0,v,1.0);
    else if (CURRENT_COLOR_MODE == 4) retcolor = vec4(v,v,1.0,1.0);
    else if (CURRENT_COLOR_MODE == 5) retcolor = vec4(1.0,v,v,1.0);
    return retcolor;
}
void main() {
    dvec2 orthoCoords = getOrthoCoordsFromWindowCoords(double(gl_FragCoord.x), double(gl_FragCoord.y));
    double normalizedVelocity = findEscapeVelocity(orthoCoords);
	fragColor = getColorFromVelocity(normalizedVelocity);
}
