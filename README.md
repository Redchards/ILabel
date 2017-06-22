# ILabel

A school project which aims to detect text and images in a document in image format written in the Java programming language.

# Technology used

- imageJ : a framework for image processing writtent in Java. This project is in fact a plugin for the standalone imageJ, but can stand on its own
- ijBlob : a small library which aims to detect connected components in an image

# How it works

First of all, we're detecting accentuated characters to make further analysis easier.
The, the algorithm is mainly using 3 heuristic in order to detect everything :
- Binary Density Heuristic
- Closure Effect Heuristic
- Class Size Heuristic

More information on how it works in the following slides with results at the end (in french) : https://drive.google.com/open?id=0B6dZXfkEU79yT1NvMmNFTmViR0U

# Can I use it in my project ?

As it stand, it's a big no. Indeed, our method is far from perfect, even if it's yielding correct results most of the time. We could've used
better algorithms like the MSER algorithm, and then develop on this method. More information about the MSER algorithm : https://en.wikipedia.org/wiki/Maximally_stable_extremal_regions
Moreover, there is far better library for this, like OpenCV.
