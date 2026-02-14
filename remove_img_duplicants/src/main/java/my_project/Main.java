package my_project;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import io.github.ksshim.jimagehash.hash.Hash;
import io.github.ksshim.jimagehash.hashAlgorithms.AverageColorHash;
import io.github.ksshim.jimagehash.hashAlgorithms.AverageHash;
import io.github.ksshim.jimagehash.hashAlgorithms.HashingAlgorithm;
import io.github.ksshim.jimagehash.hashAlgorithms.PerceptiveHash;
import io.github.ksshim.jimagehash.hashAlgorithms.RotPHash;
import my_project.CustomTypes.ImageInfo;


// import io.github.ksshim.jimagehash.hashAlgorithms.PerceptiveHash;
// import io.github.ksshim.jimagehash.hashAlgorithms.AverageHash;
// import io.github.ksshim.jimagehash.hashAlgorithms.RotPHash;
// import io.github.ksshim.jimagehash.hashAlgorithms.AverageColorHash;

public class Main {
    public static double hammingDist = 0.2;
    public static int maxIDUsed = 0;
    public static Map<Hash, ImageInfo> imageMap = new HashMap<>();
    public static Map<Integer, List<Hash>> groupMap = new HashMap<>();
    public static File topFolder;
    public static int delCount = 0;

    public static void main(String[] args)throws Exception {
        String[] testArgs = {"/mnt/Sata-SSD/PicsDB_old/Backend/contentData/test2", "0.33"};
        args = testArgs;
        readArgs(args);
        System.out.println("Processing...");

        HashingAlgorithm pHasher = new PerceptiveHash(64);
        HashingAlgorithm aHasher = new AverageHash(64);
        HashingAlgorithm rHasher = new RotPHash(64);
        HashingAlgorithm cHasher = new AverageColorHash(64);

        Stream<Path> filStream = Files.walk(topFolder.toPath());
        Iterator<Path> iter = filStream.iterator();
        iter.next(); //First element is always the root folder
        // List<File> testList = new LinkedList<>();
        // testList.add(new File("/mnt/Sata-SSD/PicsDB_old/Backend/contentData/test/a.png"));
        // testList.add(new File("/mnt/Sata-SSD/PicsDB_old/Backend/contentData/test/b.webp"));
        // testList.add(new File("/mnt/Sata-SSD/PicsDB_old/Backend/contentData/test/c.webp"));
        // testList.add(new File("/mnt/Sata-SSD/PicsDB_old/Backend/contentData/test/d.webp"));
        
        while (iter.hasNext()) {
            File file =  iter.next().toFile();
        // for(File file : testList){
           if(!isImage(file)) continue;
            BufferedImage img;
            try {
                img = ImageIO.read(file);
            } catch (Exception e) {
                System.err.println("File >" + file.getAbsolutePath() + "< could not be read. Probaply a Bug in com.twelvemonkeys.imageio");
                System.err.println("ignoring file...");
                continue;
            }
                        
            Hash pHash = pHasher.hash(img);
            Hash aHash = aHasher.hash(img);
            Hash rHash = rHasher.hash(img);
            Hash cHash = cHasher.hash(img);
            ImageInfo imgInfo = new ImageInfo(file, -1, pHash, aHash, rHash, cHash);
            handleImage(imgInfo);
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
                throw new IllegalArgumentException("second parameter provided is not an Integer: " + args[1]);
            }
        }
    }

    private static void handleImage(ImageInfo imgInfo) {
        //delte duplicant
        boolean isExactDuplicant = handleDuplicant(imgInfo);
        if(isExactDuplicant) return;
        handleSimilar(imgInfo);
    }

    private static void handleSimilar(ImageInfo newImgInfo) {

        for(Hash oldHash : imageMap.keySet()){
            ImageInfo oldImgInfo = imageMap.get(oldHash);
            if(myHammingDistance(oldImgInfo, newImgInfo) <= hammingDist){
                if(newImgInfo.groupID == -1){
                    newImgInfo = newSimilarImg(newImgInfo, oldImgInfo);
                }
                else{
                    newImgInfo = updateSimilarImg(oldImgInfo, newImgInfo);
                }
            }
        }

        imageMap.put(newImgInfo.pHash, newImgInfo);
        if(newImgInfo.groupID != -1){
            groupMap.get(newImgInfo.groupID).add(newImgInfo.pHash);
        }
        

    }

    private static double myHammingDistance(ImageInfo oldImgInfo, ImageInfo newImgInfo) {
        double pDistance = newImgInfo.pHash.normalizedHammingDistance(oldImgInfo.pHash);
        double aDistance = newImgInfo.aHash.normalizedHammingDistance(oldImgInfo.aHash);
        double rDistance = newImgInfo.rHash.normalizedHammingDistance(oldImgInfo.rHash);
        double cDistance = newImgInfo.cHash.normalizedHammingDistance(oldImgInfo.cHash);

        double pWeight = 0.6;
        double aWeight = 1;
        double rWeight = 0.4;
        double cWeight = 0.1;

        double result = (pDistance * pWeight) + (aDistance * aWeight) + (rDistance * rWeight) + (cDistance * cWeight);
        result = result/(pWeight + aWeight + rWeight + cWeight);

        return result;
    }

    private static boolean handleDuplicant(ImageInfo newImgInfo) {
        if(imageMap.containsKey(newImgInfo.pHash)){
            ImageInfo oldImgInfo = imageMap.get(newImgInfo.pHash);

            //Preserve deepest Folder structure
            int fileDepth = countChars(newImgInfo.file.getAbsolutePath(), File.separatorChar);
            int oldFileDepth = countChars(oldImgInfo.file.getAbsolutePath(), File.separatorChar);
            if(fileDepth<= oldFileDepth){
                newImgInfo.file.delete();
            }
            else{
                imageMap.replace(newImgInfo.pHash, newImgInfo);
                oldImgInfo.file.delete();
            }
            delCount++;
            return true;
        }
        return false;
    }

    private static int countChars(String string, char matchC) {
        int count = 0;
        for(char c : string.toCharArray()){
            if(c == matchC) count++;
        }
        return count;
    }


    private static ImageInfo newSimilarImg(ImageInfo newImgInfo, ImageInfo oldImgInfo) {
        if(oldImgInfo.groupID == -1){
            maxIDUsed++;
            oldImgInfo.groupID = maxIDUsed;
            groupMap.putIfAbsent(oldImgInfo.groupID, new LinkedList<>());
            groupMap.get(oldImgInfo.groupID).add(oldImgInfo.pHash);
        }
        newImgInfo.groupID = oldImgInfo.groupID;
        return newImgInfo;
    }

    private static ImageInfo updateSimilarImg(ImageInfo oldImgInfo, ImageInfo newImgInfo) {
        if(oldImgInfo.groupID == -1){
            oldImgInfo.groupID = newImgInfo.groupID;
            groupMap.get(oldImgInfo.groupID).add(oldImgInfo.pHash);
        }
        else if(oldImgInfo.groupID != newImgInfo.groupID){
            int newGroup = mergeGroups(oldImgInfo.groupID, newImgInfo.groupID);
            newImgInfo.groupID = newGroup;
        }
        return newImgInfo;
    }

    private static int mergeGroups(int id1, int id2) {
        List<Hash> affectedHashes = groupMap.get(id2);
        for(Hash affHash : affectedHashes){
            imageMap.get(affHash).groupID = id1;
        }
        groupMap.get(id1).addAll(affectedHashes);
        groupMap.remove(id2);
        return id1;
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