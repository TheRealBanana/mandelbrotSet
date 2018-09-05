# mandelbrotSet

More boredom means more coding. Decided to do what everyone and their grandma's has already done: Visualize the Mandelbrot Set. 

This code is super ugly because I wanted to make it without looking at any other code, just me and the maths. 
In paricular I'm not quite sure how best to color based on escape velocities (or even how to calculate the escape velocity in a sane way)

At least it prints something though so I'm happy. Recoding using GLSL sped the performance up immensely even in fp64 mode.
Unfortunately GLSL's fp64 extension doesn't include trigonometric functions so the only color modes are basic modes for now.

Found an interesting coloring scheme using sine and cosine functions with the escape velocity (both normalized to the max number of iterations and unnormalized) shown below. Will be interesting to see how the color evolves as we get pan/zoom implemented.

Tried to remake the "deep-zoom" animation on wikipedia. Couldnt zoom the whole way without arbitrary precision however.  
[Animated "Deep Zoom" - 4000 iterations](https://i.imgur.com/QVky6WX.mp4)  
[Animated "Deep Zoom" - 6000 iterations](https://i.imgur.com/Kk3XzEy.mp4)  
[Animated "Deep Zoom" - 10000 iterations](https://i.imgur.com/qJxoWdr.mp4)  

I forgot that adjusting the max number of iterations produces better (and slower) results at higher numbers. 
Here's some results @1000 iteration limit (instead of the above 50-iteration limit):
![Screenshot](https://i.imgur.com/ihgfHYO.png)
More pretty pictures....
![Screenshot](https://i.imgur.com/lah8sKX.png)
Here's a nice illustration of how sensitive the patterns are to the maximum number of iterations. 
Compare this screenshot at 200 iterations max to the one below at 250 iterations max.
![Screenshot](https://i.imgur.com/Zjo5xDg.png)
![Screenshot](https://i.imgur.com/gTjPZN8.png)
Implemented a basic color ramp in HSV space varying only the hue using the normalized velocity. Works rather well:
![Screenshot](https://i.imgur.com/Wa1MHxC.png)
I had no clue how to convert HSV to RGB values but found some awesome code on lolengine.net that works perfectly.
![Screenshot](https://image.ibb.co/nnOvtU/mandelbrot_colorscheme_hsvramp2.png)
