# mandelbrotSet

More boredom means more coding. Decided to do what everyone and their grandma's has already done: Visualize the Mandelbrot Set. 

This code is super ugly because I wanted to make it without looking at any other code, just me and the maths. 
In paricular I'm not quite sure how best to color based on escape velocities (or even how to calculate the escape velocity in a sane way)

At least it prints something though so I'm happy. Multithreading will also need to come since this is super slow.


Found an interesting coloring scheme using sine and cosine functions with the escape velocity (both normalized to the max number of iterations and unnormalized) shown below. Will be interesting to see how the color evolves as we get pan/zoom implemented.
![Screenshot](https://i.imgur.com/u3TwINn.jpg)
