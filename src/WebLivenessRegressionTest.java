import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WebLivenessRegressionTest {
    private final static long TIMESTAMP = System.currentTimeMillis();
    private final static String TRANSACTION_ID = formattedDateTime(TIMESTAMP) + "_" + uuidV4();
    private final static int CLIENT_ID = 114;
    private final static String API_KEY = "vHj9YoXkuOV1WZwGjqyXQyRTPIoi8iluivfoFpUP8dhDmPFq44AenOrcHRdxHHee";
    private final static String URL = "https://research3.discoverelement.com:9443/api/ers/spoof";

    public static void main(String[] args) throws IOException {
        long begin = System.currentTimeMillis();

        Path path = Paths.get("/Users/yongmu/Downloads/bri-data");
        //Path path = Paths.get("/Users/yongmu/Downloads/20200915-173844_042747ec-43fa-4490-8cb3-f541058d4339");
        List<Path> paths = findByFileExtension(path, ".png");
        List<List<String>> rows = new ArrayList<>();

        int counter = 1;
        for(Path p : paths) {
            System.out.println("processing " + p.toString());
            LivenessResult result = checkLiveness(counter, p);
            if(result.features != null) {
                System.out.println("feature size = " + result.features.size());
                for(SpoofResponseResult feature : result.features) {
                    //System.out.println(String.format("%s iou = %p.toString() + ": iou ratio = " + feature.iouRatio);
                    //if(feature.iouRatio > 0) {
                        //sumIouRatio += feature.iouRatio;
                        //cntIouRatio += 1;
                    //}

                    List<String> row = new ArrayList<>();

                    row.add(String.valueOf(counter));

                    row.add(p.getFileName().toString());
                    row.add(getCorner(feature.cornerIndex));
                    row.add(result.sessionId);
                    row.add(feature.fileName);
                    row.add(String.valueOf(result.spoof));
                    row.add(String.valueOf(result.noFace));
                    row.add(String.valueOf(result.gazePassed));
                    row.add(String.valueOf(feature.iouRatio));
                    row.add(String.valueOf(result.thresholds.MASK_TYPE_DEFAULT_AREA_RATIO));
                    row.add(String.valueOf(feature.fas_multi));
                    row.add(String.valueOf(result.thresholds.MULTI_THRESHOLD));
                    row.add(String.valueOf(feature.fas_gazeY));
                    row.add(String.valueOf(result.thresholds.GAZEY_TOP_THRESHOLD));
                    row.add(String.valueOf(result.thresholds.GAZEY_BOTTOM_THRESHOLD));
                    row.add(String.valueOf(result.thresholds.GAZEY_YDIFF_THRESHOLD));
                    row.add(String.valueOf(feature.fas_cutout));
                    row.add(String.valueOf(result.thresholds.CUTOUT_THRESHOLD));
                    row.add(String.valueOf(feature.fas_print));
                    row.add(String.valueOf(result.thresholds.PRINT_THRESHOLD));
                    row.add(String.valueOf(feature.fas_screen));
                    row.add(String.valueOf(result.thresholds.SCREEN_THRESHOLD));

                    rows.add(row);
                }
            }
            counter++;
        }
        System.out.println("all threads finished");

        List<String> headerLine = Arrays.asList("Request Number", "Frontend File Name",
                "Corner Index", "Backend Session Id", "Backend File Name",
                "Liveness Passed", "No Face", "Gaze Passed",
                "Iou Ratio", "Iou Ratio Threshold",
                "fas_multi", "fas_multi threshold",
                "fas_gazeY", "fas_gazeY top threshold", "fas_gazeY bottom threshold", "fas_gazeY diff threshold",
                "fas_cutout", "fas_cutout threshold",
                "fas_print", "fas_print threshold",
                "fas_screen", "fas_screen threshold");

        writeCSV(TRANSACTION_ID + ".csv", headerLine, rows);

        long end = System.currentTimeMillis();

        System.out.println("Time cost: " + (end - begin) / (60 * 1000) + " mins");

        /*if(cntIouRatio > 0) {
            double averageIouRatio = sumIouRatio / cntIouRatio;
            System.out.println("Average iou ratio = " + averageIouRatio);
        }*/
    }

    private static String getCorner(int cornerIndex) {
        switch (cornerIndex) {
            case -1:
                return "na";
            case 0:
                return "tl";
            case 1:
                return "tr";
            case 2:
                return "bl";
            case 3:
                return "br";
            default:
                return null;
        }
    }

    private static String getFileNameFromIndex(String folder, int cornerIndex) throws IOException {
        Path folderPath = Paths.get(folder);
        List<Path> files = findByFileExtension(folderPath, ".png");

        for(Path file : files) {
           String fileName = file.getFileName().toString();
            String fileNameWithoutExt = fileName.replaceFirst("[.][^.]+$", "");
            String[] splitted = fileNameWithoutExt.split("_");
            if(splitted.length == 3) {
                if(splitted[0].equalsIgnoreCase("00") || splitted[0].equalsIgnoreCase("01") ||
                    splitted[0].equalsIgnoreCase("02") || splitted[0].equalsIgnoreCase("03"))
                    if(Integer.valueOf(splitted[0]) == cornerIndex) return fileName;
            }
        }

        return null;
    }

    private static void writeCSV(String fileName, List<String> headerLine, List<List<String>> rows) {
        try(FileWriter csvWriter = new FileWriter(fileName)) {

            csvWriter.append(String.join(",", headerLine));
            csvWriter.append("\n");

            for (List<String> rowData : rows) {
                csvWriter.append(String.join(",", rowData));
                csvWriter.append("\n");
            }

            csvWriter.flush();
            //csvWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static LivenessResult checkLiveness(int rowNo, Path p) throws IOException {
        LivenessRequest request = new LivenessRequest();
        request.txnId = TRANSACTION_ID;
        request.images = new ArrayList<Image>();

        Image image = new Image();
        image.data = base64EncodeImage(p);
        image.index = 0;
        image.tag = "tl";
        request.images.add(image);

        String json = toJson(request);
        Map<String, String> headers = new HashMap<>();
        headers.put("CLIENTID", String.valueOf(CLIENT_ID));
        headers.put("TIMESTAMP", String.valueOf(TIMESTAMP));
        headers.put("HASHTOKEN", getHashtoken());

        HttpResp resp = postJsonWithHeaders(URL, json, headers);

        LivenessResult result = parseResult(rowNo, resp);

        if(result == null) System.out.println(String.format("result == null, p = %s, response code = %d, errorMessage = %s",
                p.toString(), resp.responseCode, resp.errorMessage));

        return result;
    }

    private static LivenessResult parseResult(int rowNo, HttpResp response) {
        LivenessResult result = null;

        if(response.responseCode == HttpURLConnection.HTTP_OK) {
            try {
                System.out.println(String.format("%d result = %s", rowNo, response.response));
            	result = fromJson(response.response, LivenessResult.class);

            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    private static String toJson(Object object) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        String json = null;
        try {
            json = mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return json;
    }

    private static <T> T fromJson(String src, Class<T> clazz) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        T obj = null;

        try {
            obj = mapper.readValue(src, clazz);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return obj;
    }

    private static class HttpResp {
        public int responseCode;
        public String errorMessage;
        public String response;
        public String toString() {
        	return "responseCode: " + responseCode + ", errorMessage: " + errorMessage + ", response: " + response;
        }
    }

    public static HttpResp postJsonWithHeaders(String url, String json, Map<String, String> headers) {
        try {
            URL httpUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) httpUrl.openConnection();
            conn.setConnectTimeout(5000);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            if(headers != null) {
				for(Map.Entry<String, String> entry : headers.entrySet()) {
					conn.setRequestProperty(entry.getKey(), entry.getValue());
				}
			}
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");

            OutputStream os = conn.getOutputStream();
            os.write(json.getBytes("UTF-8"));
            os.close();

            return getHttpResponse(conn);
        } catch(Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static HttpResp getHttpResponse(HttpURLConnection conn) throws IOException {
    	HttpResp response = new HttpResp();
        response.responseCode = conn.getResponseCode();
        if (conn.getResponseCode() >= 400) {
            // read the response
            InputStream in = new BufferedInputStream(conn.getErrorStream());
            response.errorMessage = IOUtils.toString(in, "UTF-8");

            in.close();
        } else {
            // read the response
            InputStream in = new BufferedInputStream(conn.getInputStream());
            response.response = IOUtils.toString(in, "UTF-8");

            in.close();
        }

        conn.disconnect();

        return response;
    }

    private static String base64EncodeImage(Path imagePath) {
        String base64Image = "";
        File file = new File(imagePath.toString());

        try (FileInputStream imageInFile = new FileInputStream(file)) {
            // Reading a Image file from file system
            byte imageData[] = new byte[(int) file.length()];
            imageInFile.read(imageData);
            base64Image = Base64.getEncoder().encodeToString(imageData);
        } catch (FileNotFoundException e) {
            System.out.println("Image not found" + e);
        } catch (IOException ioe) {
            System.out.println("Exception while reading the Image " + ioe);
        }

        return base64Image;
    }

    private static String getTagFromFileName(Path imagePath) {
        String tag = null;

        String fileName = imagePath.getFileName().toString();
        String fileNameWithoutExt = fileName.replaceFirst("[.][^.]+$", "");
        String[] splitted = fileNameWithoutExt.split("_");
        if(splitted.length == 3) {
            if(splitted[1].equalsIgnoreCase("tl") || splitted[1].equalsIgnoreCase("tr") ||
                splitted[1].equalsIgnoreCase("bl") || splitted[1].equalsIgnoreCase("br") ||
                splitted[1].equalsIgnoreCase("na"))
                tag = splitted[1];
        }

        return tag;
    }

    private static int getIndexFromFileName(Path imagePath) {
        String fileName = imagePath.getFileName().toString();
        String fileNameWithoutExt = fileName.replaceFirst("[.][^.]+$", "");
        String[] splitted = fileNameWithoutExt.split("_");
        if(splitted.length == 3) {
            if(splitted[0].equalsIgnoreCase("00") || splitted[0].equalsIgnoreCase("01") ||
                splitted[0].equalsIgnoreCase("02") || splitted[0].equalsIgnoreCase("03"))
                return Integer.valueOf(splitted[0]);
        }

        return -1;
    }

    private static String formattedDateTime(long epoch) {
        LocalDateTime ldt = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDateTime();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ENGLISH);
        return ldt.format(formatter);
    }

    private static String uuidV4() {
        return UUID.randomUUID().toString();
    }

    private static String getHashtoken() {
        String payload = API_KEY + TIMESTAMP + TRANSACTION_ID;
        return sha256(payload);
    }

    private static String sha256(String payload) {
        MessageDigest messageDigest = null;
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] bytes = messageDigest.digest(payload.getBytes());
        String hash = new String(Base64.getEncoder().encode(bytes));
        return hash;
    }

    public static List<Path> findByFileExtension(Path path, String fileExtension)
            throws IOException {

        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path must be a directory!");
        }

        List<Path> result;
        try (Stream<Path> walk = Files.walk(path)) {
            result = walk.filter(Files::isRegularFile)   // is a file
                    .filter(p -> p.getFileName().toString().endsWith(fileExtension))
                    .collect(Collectors.toList());
        }
        return result;

    }

    private static class LivenessRequest {
        String txnId;
        List<Image> images;
        String action = "spoof";
    }

    private static class Image {
        int index = 0;
        String data;
        String tag;
        String modality = "face";
    }

    private static class LivenessResult {
        public boolean spoof;
        public boolean noFace;
        public boolean gazePassed;
        public int faceNotDetectedBlazeface;
        public int faceNotDetectedBlazefaceThreshlod;
        public ArrayList<SpoofResponseResult> features;
        public String sessionId;
        public Thresholds thresholds;
    }

    private static class Thresholds {
        public Double CUTOUT_THRESHOLD;
        public Double MULTI_THRESHOLD;
        public Double SCREEN_THRESHOLD;
        public Double PRINT_THRESHOLD;
        public Double GAZEY_TOP_THRESHOLD;
        public Double GAZEY_BOTTOM_THRESHOLD;
        public Double GAZEY_YDIFF_THRESHOLD;
        public Double MASK_TYPE_DEFAULT_WIDTH_RATIO;
        public Double MASK_TYPE_DEFAULT_HEIGHT_RATIO;
        public Double MASK_TYPE_DEFAULT_AREA_RATIO;
    }

    public static class SpoofResponseResult {
        public float fas_cutout;
        public float fas_gazeY;
        public float fas_print;
        public float fas_screen;
        public float fas_multi;
        public double iouRatio;
        public int cornerIndex;
        public int index;
        public String fileName;
    }
}
