package com.heima.minio;


import com.heima.file.service.FileStorageService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

@SpringBootTest(classes = MinIOApplication.class)
@RunWith(SpringRunner.class)
public class MinIOTest {

    @Autowired
    FileStorageService fileStorageService;

    /**
     * 把文件上传到minio并可进行访问
     */
    @Test
    public void test() throws FileNotFoundException {
        FileInputStream fileInputStream = new FileInputStream("D:\\Workspace\\list.html");
        String path = fileStorageService.uploadHtmlFile("", "list3.html", fileInputStream);
        System.out.println(path);
    }

    /**
     * 把文件上传到minio并可进行访问
     * @param args
     */
//    public static void main(String[] args) {
//
//        try {
//            FileInputStream fileInputStream = new FileInputStream("D:\\Workspace\\list.html");
//            //1. 创建minio client
//            MinioClient minioClient = MinioClient.builder().
//                    credentials("minio", "minio123").
//                    endpoint("http://192.168.200.130:9000").build();
//            //2. 上传
//            PutObjectArgs putObjectArgs = PutObjectArgs.builder().
//                    object("list2.html").
//                    contentType("text/html").
//                    bucket("leadnews").
//                    stream(fileInputStream, fileInputStream.available(), -1).
//                    build();
//            minioClient.putObject(putObjectArgs);
//            //3. 访问路径
//            System.out.println("http://192.168.200.130:9000/leadnews/list2.html");
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
}
