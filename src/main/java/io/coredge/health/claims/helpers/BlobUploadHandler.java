package io.coredge.health.claims.helpers;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import io.coredge.health.claims.config.properties.ApplicationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

@Component
public class BlobUploadHandler {

    private final ApplicationProperties.AzureStorage azureStorageProperties;

    public BlobUploadHandler(ApplicationProperties.AzureStorage azureStorageProperties) {
        this.azureStorageProperties = azureStorageProperties;
    }

    // get file and then upload to blocb storage account
    public String uploadToBlobStorage(String fileUrl, String claimId) throws IOException {
        String blobUrl = "", fullFolderPathWithFile = "", fileName = "";

        fileName = Common.getLastPartToURL(fileUrl);
        fullFolderPathWithFile = String.format("%s/%s", CreateFolderPath(azureStorageProperties.getPrefixFolder(), claimId), fileName);

        // blob masala
        if (fileUrl != null && !fileUrl.isEmpty()) {
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(azureStorageProperties.getStorageEndpoint()).buildClient();
            BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(azureStorageProperties.getBlobServiceClient());
            BlobClient blobClient = blobContainerClient.getBlobClient(fullFolderPathWithFile);
            blobClient.copyFromUrl(fileUrl);
            return blobClient.getBlobUrl();
        }
        return "";
    }

    // for converting file in byte array
    private byte[] getFileBytes(String fileUrl) throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(fileUrl, byte[].class);
    }

    // for downloading file and deleting downloaded file
    private File downloadFile(String fileUrl, String fileName) throws IOException {
        RestTemplate restTemplate = new RestTemplate();
        byte[] fileData = restTemplate.getForObject(fileUrl, byte[].class);

        File file = new File(fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            if (fileData != null) {
                fos.write(fileData);
            }
        }
        return file;
    }

    // creating folder path without pre and sub fix folder
    private String CreateFolderPath() {
        return CreateFolderPath(null, null);
    }

    // creating blob folder path
    private String CreateFolderPath(String prefixFolder, String subfixFolder) {
        String path = "", year = "", month = "", day = "";
        LocalDate currentDate = LocalDate.now();
        year = String.valueOf(currentDate.getYear());
        month = currentDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        day = String.format("%02d", currentDate.getDayOfMonth());
        path = String.format("%s/%s/%s", year, month, day);
        if (prefixFolder != null && !prefixFolder.trim().isEmpty()) {
            path = String.format("%s/%s", prefixFolder.trim(), path);
        }
        if (subfixFolder != null && !subfixFolder.trim().isEmpty()) {
            path = String.format("%s/%s", path, subfixFolder.trim());
        }
        return path;
    }
}
