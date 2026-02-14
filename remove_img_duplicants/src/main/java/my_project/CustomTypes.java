package my_project;

import java.io.File;

public class CustomTypes {

    public static class ImageInfo{
        public File file;
        public int groupID;
        
        public ImageInfo(File file, int groupID) {
            this.file = file;
            this.groupID = groupID;
        }
    }
}
