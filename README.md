# mandelbrotSet

More boredom means more coding. Decided to do what everyone and their grandma's has already done: Visualize the Mandelbrot Set. 

This code is super ugly because I wanted to make it without looking at any other code, just me and the maths. 
In paricular I'm not quite sure how best to color based on escape velocities (or even how to calculate the escape velocity in a sane way)

At least it prints something though so I'm happy. Recoding using GLSL will be necessary for anything approaching realtime performance.


Found an interesting coloring scheme using sine and cosine functions with the escape velocity (both normalized to the max number of iterations and unnormalized) shown below. Will be interesting to see how the color evolves as we get pan/zoom implemented.
![Screenshot](https://i.imgur.com/VwPGoYX.png)
![Screenshot](https://i.imgur.com/AIPmvc6.png)
![Screenshot](https://i.imgur.com/w5i0v0U.png)
I forgot that adjusting the max number of iterations produces better (and slower) results at higher numbers. 
Here's some results @1000 iteration limit (instead of the above 50-iteration limit):
![Screenshot](https://i.imgur.com/ihgfHYO.png)


Reimplementing in GLSL will speed things up a bit but will take a lot of work. I'm hoping implementing basic threading will be enough of a speedup before I tackle using shaders.