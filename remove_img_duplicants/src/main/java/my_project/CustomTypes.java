package my_project;

import java.io.File;

import io.github.ksshim.jimagehash.hash.Hash;

public class CustomTypes {

    public static class ImageInfo{
        public File file;
        public int groupID;
        public Hash pHash;
        public Hash aHash;
        public Hash rHash;
        public Hash cHash;
        
        public ImageInfo(File file, int groupID, Hash pHash, Hash aHash, Hash rHash, Hash cHash) {
            this.file = file;
            this.groupID = groupID;
            this.pHash = pHash;
            this.aHash = aHash;
            this.rHash = rHash;
            this.cHash = cHash;
        }
    
    }
}
