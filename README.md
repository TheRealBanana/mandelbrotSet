# mandelbrotSet

More boredom means more coding. Decided to do what everyone and their grandma's has already done: Visualize the Mandelbrot Set. 

This code is super ugly because I wanted to make it without looking at any other code, just me and the maths. 
In paricular I'm not quite sure how best to color based on escape velocities (or even how to calculate the escape velocity in a sane way)

At least it prints something though so I'm happy. Recoding using GLSL will be necessary for anything approaching realtime performance.


Found an interesting coloring scheme using sine and cosine functions with the escape velocity (both normalized to the max number of iterations and unnormalized) shown below. Will be interesting to see how the color evolves as we get pan/zoom implemented.
![Screenshot](https://i.imgur.com/VwPGoYX.png)

Well after getting the zooming implemented I've found I am bumping against a resolution limit. This isn't surprising considering I'm using plain Double's for everything

![Screenshot](https://i.imgur.com/AIPmvc6.png)
![Screenshot](https://i.imgur.com/w5i0v0U.png)

Zooming anymore than that produces banding artifacts, likely because the resolution limit maths break down without excess precision. 
I tried implementing everything using BigDecimals but it was horribly slow.


Reimplementing in GLSL will speed things up a bit but the resolution limit is a different problem entirely.