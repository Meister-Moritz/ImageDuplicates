package my_project;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import io.github.ksshim.jimagehash.hash.Hash;
import io.github.ksshim.jimagehash.hashAlgorithms.HashingAlgorithm;
import io.github.ksshim.jimagehash.hashAlgorithms.PerceptiveHash;
import my_project.CustomTypes.ImageInfo;

public class Main {
    public static double hammingDist = 5;
    public static int maxIDUsed = 0;
    public static Map<Hash, ImageInfo> imageMap = new HashMap<>();
    public static File topFolder;
    public static int delCount = 0;

    public static void main(String[] args)throws Exception {
        String[] testArgs = {"/mnt/Sata-SSD/Rule22/contentData2/test", "5"};
        args = testArgs;
        readArgs(args);
        System.out.println("Processing...");
        HashingAlgorithm hasher = new PerceptiveHash(64);
        Stream<Path> filStream = Files.walk(topFolder.toPath());
        Iterator<Path> iter = filStream.iterator();
        iter.next(); //First element is always the root folder

        while (iter.hasNext()) {
            File file =  iter.next().toFile();
            if(!isImage(file)) continue;
            BufferedImage img;
            try {
                img = ImageIO.read(file);
            } catch (Exception e) {
                System.err.println("File >" + file.getAbsolutePath() + "< could not be read. Probaply a Bug in com.twelvemonkeys.imageio");
                System.err.println("ignoring file...");
                continue;
            }
                        
            Hash imgHash = hasher.hash(img);
            handleImage(file, imgHash);
        }
        filStream.close();

        writeChanges();
        System.out.println(delCount + " files deleted");
    }

    private static void writeChanges() {
        for(Hash key : imageMap.keySet()){
            File file = imageMap.get(key).file;
            int groupID = imageMap.get(key).groupID;
            if(groupID == -1) continue;
            String relativePath = file.getAbsolutePath().substring(topFolder.getAbsolutePath().length());
            moveImage(file, relativePath, groupID);
        }
    }

    private static void readArgs(String[] args) {
        if(args.length < 1){
            throw new IllegalArgumentException("missing arguments, do: program.jar /folder");
        }
        topFolder = new File(args[0]);
        if(!topFolder.isDirectory()){
            throw new IllegalArgumentException("provided Path is not a directory");
        }
        if(args.length == 2){
            try {
                hammingDist = Double.parseDouble(args[1].replace(',', '.'));
            } catch (Exception e) {
                throw new IllegalArgumentException("second parameter provided is not a Double: " + args[1]);
            }
        }
    }

    private static void handleImage(File file, Hash imgHash) {
        boolean editNewImg = false;
        if(imageMap.containsKey(imgHash)){
            ImageInfo oldInfo = imageMap.get(imgHash);

            //Preserve deepest Folder structure
            int fileDepth = countChars(file.getAbsolutePath(), File.separatorChar);
            int oldFileDepth = countChars(oldInfo.file.getAbsolutePath(), File.separatorChar);
            if(fileDepth<= oldFileDepth){
                file.delete();
            }
            else{
                imageMap.replace(imgHash, new ImageInfo(file, oldInfo.groupID));
                oldInfo.file.delete();
            }
            delCount++;
            return;
        }
        Map<Hash, ImageInfo> newEntrys = new HashMap<>();
        for(Hash hashKey : imageMap.keySet()){
            if(hashKey.hammingDistance(imgHash) <= hammingDist){
                handleSimilar(imgHash, file, hashKey, editNewImg, newEntrys);
                editNewImg = true;
            }
            
        }
        if(newEntrys.isEmpty()){
            imageMap.put(imgHash, new ImageInfo(file, -1));
        }
        else{
            imageMap.putAll(newEntrys);
        }
        
        

    }

    private static int countChars(String string, char matchC) {
        int count = 0;
        for(char c : string.toCharArray()){
            if(c == matchC) count++;
        }
        return count;
    }

    private static void handleSimilar(Hash newHash, File newfile, Hash matchingHash, boolean editNewImg,Map<Hash, ImageInfo> newEntrys) {
        ImageInfo oldImgInfo = imageMap.get(matchingHash);
        if(oldImgInfo.groupID == -1){
            maxIDUsed++;
            oldImgInfo.groupID = maxIDUsed;
        }
        
        if(!editNewImg){
            newEntrys.put(newHash, new ImageInfo(newfile, oldImgInfo.groupID));
        }
    }


    private static void moveImage(File file, String relativePath, int groupID) {
        String newFolder  = topFolder.getAbsolutePath() + File.separatorChar + "similar" + File.separatorChar + groupID + File.separatorChar;
        String fileName = relativePath.replace(File.separatorChar, '-');
        if(fileName.startsWith("-")){
            fileName = fileName.substring(1);
        }
        File targetFile = new File(newFolder + fileName);
        if(targetFile.exists()){
            int dotIndex = file.getName().lastIndexOf(".");
            String suffix = file.getName().substring(dotIndex).toLowerCase();
            fileName = fileName.substring(0, dotIndex);
            String time = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            targetFile = new File(newFolder + fileName + "_" + time + suffix);
        }
        try {
            Files.createDirectories(targetFile.toPath().getParent());
            Files.move(file.toPath(), targetFile.toPath());
        } catch (Exception e) {
            System.err.println("File >" + file.getAbsolutePath() + "< could not be moved");
        }
    }



    private static boolean isImage(File file) {
        if(file.isFile()){
            int dotIndex = file.getName().lastIndexOf(".");
            String suffix = file.getName().substring(dotIndex).toLowerCase();
            if(suffix.equals(".png")
            || suffix.equals(".webp")
            || suffix.equals(".jpg")
            || suffix.equals(".jpeg")){
                return true;
            }
        }
        return false;
    }
}