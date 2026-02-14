# Image Duplicates
This program deletes all duplicated images in a given folder (and subfolders).<br>
It also collects similar pictures and groups them in ./similar/groupID/.<br>
The filename will represent the folder the pictures came from.<br>
- /a/b/c/img.png => similar/1/a-b-c/img.png

Supported Formats are: .png, .jpg, .jpeg, .webp

## How to use:
1. Download ./builds/remove_img_duplicants__2026_02_14.jar.
2. Run java -jar remove_img_duplicants__2026_02_14.jar /folderPath [hammingDistance].
    - /folderPath is the path to the folder your images are in.
    - hammingDistance defines the threshold for images to be considered similar.
It's optional (default=5).

### Note:
The hammingDistance-Argument is only used for grouping images.<br>
Images will only be deleted when the hammingDistance is 0.<br>
(HammingDistance=0 is not a 100% guarantee that images are the same, but I never had problems with it).