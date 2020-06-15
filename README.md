# mandelbrotSet

More boredom means more coding. Decided to do what everyone and their grandma's has already done: Visualize the Mandelbrot Set. 

Created some zooms through the iteration count instead of the magnification:
All of them can be found here (albiet in a highly compressed form): https://imgur.com/a/a6kfIwd

Tried to remake the "deep-zoom" animation on wikipedia. Couldnt zoom the whole way without arbitrary precision however.  
[Animated "Deep Zoom" - 4000 iterations](https://i.imgur.com/QVky6WX.mp4)  
[Animated "Deep Zoom" - 6000 iterations](https://i.imgur.com/Kk3XzEy.mp4)  
[Animated "Deep Zoom" - 10000 iterations](https://i.imgur.com/r4sd6Wv.mp4)  
[An interesting zoom I found - 2000 iterations](https://i.imgur.com/jc0Xrsx.mp4)  

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

Recently added a bifurcation diagram mode (shortcut key is B). Here's a few pictures of it:
![Screenshot](https://i.imgur.com/m9iJi5j.png)
![Screenshot](https://i.imgur.com/sD61wuv.png)
![Screenshot](https://i.imgur.com/rlhxM7K.png)
The yellow box shows the area we are zooming into for the next image.