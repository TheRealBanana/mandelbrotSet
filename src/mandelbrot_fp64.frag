#version 430
#extension GL_ARB_gpu_shader_fp64 : require

layout(location = 0) uniform int WINDOW_SIZE_WIDTH;
layout(location = 1) uniform int WINDOW_SIZE_HEIGHT;
layout(location = 2) uniform int CURRENT_COLOR_MODE;
layout(location = 3) uniform int ESCAPE_VELOCITY_TEST_ITERATIONS;
layout(location = 4) uniform double ORTHO_WIDTH;
layout(location = 5) uniform double ORTHO_HEIGHT;
layout(location = 6) uniform double BOUND_LEFT;
layout(location = 7) uniform double BOUND_BOTTOM;

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

dvec2 complexAdd(dvec2 c1, dvec2 c2) {
    return dvec2(c1.x+c2.x, c1.y+c2.y);
}
dvec2 complexMulti(dvec2 c1, dvec2 c2) {
    double imag = (c1.x * c2.y) + (c1.y * c2.x);
    double real = (c1.x * c2.x) + (c1.y * c2.y * -1.0);
    return dvec2(real,imag);
}
//Holy crap I had no idea how expensive Sqrt() calls were.
// I'd seen the expression `(c.real*c.real) + (c.imag*c.imag) < 4.0` before in some code examples online but
// never used it because I wasn't why they were doing it. Sqrt calls are expensive, very expensive, even in
// the obvious case of sqrt(4)=2. By squaring both sides of the equation `sqrt((c.real*c.real)+(c.imag*c.imag))=2`
// we get rid of the expensive square root operation. The branching if statements in the old complexMagnitude()
// function also contributed the negative performance.
//
// Some idea of the gains. The worst case scenario, performance wise, is where z=z^2+c has to be calculated to the
// full iteration count and never diverges past 2 (or 4 with the new maths). We can get this by zooming into the
// very center (0.0, 0.0) until there is only black visible. This is where every point on the screen is checked fully.
// On my PC the time to render at 500 iterations at zoom level 10 @ corrds (-0.5, 0.0) was
// old math: 276ms
// new math: 72ms
// That's a pretty cool improvement but I still wonder what else can be done. I'm sure theres a nice way to make the
// complexMulti() function faster so I will look into that next. I know there is faster code out there but I don't want
// to implement anything I don't completely and totally understand first.
float findEscapeVelocity(dvec2 c) {
    dvec2 z = dvec2(0.0, 0.0);
    int iter = 1;
    while ((z.x*z.x) + (z.y*z.y) < 4.0 && iter < ESCAPE_VELOCITY_TEST_ITERATIONS) {
        z = complexMulti(z, z);
        z = complexAdd(z, c);
        iter++;
    }
    if ((z.x*z.x) + (z.y*z.y) >= 4.0) {
        return float(iter)/float(ESCAPE_VELOCITY_TEST_ITERATIONS);
    }
    return 0.0;
}
dvec2 getOrthoCoordsFromWindowCoords(double x, double y) {
    double orthoX = (x/double(WINDOW_SIZE_WIDTH) * ORTHO_WIDTH) + BOUND_LEFT;
    double orthoY = (y/double(WINDOW_SIZE_HEIGHT) * ORTHO_HEIGHT) + BOUND_BOTTOM;
    return dvec2(orthoX, orthoY);
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
    dvec2 orthoCoords = getOrthoCoordsFromWindowCoords(double(gl_FragCoord.x), double(gl_FragCoord.y));
    float normalizedVelocity = findEscapeVelocity(orthoCoords);
	fragColor = getColorFromVelocity(normalizedVelocity);
}
