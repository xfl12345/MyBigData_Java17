package cc.xfl12345.mybigdata.server.config;

import cc.xfl12345.mybigdata.server.plugin.apache.vfs.SpringBootResourceFileProvider;
import cc.xfl12345.mybigdata.server.service.VfsWebDavService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.impl.DefaultFileSystemManager;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.commons.vfs2.provider.ftps.FtpsFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.ram.RamFileProvider;
import org.apache.commons.vfs2.provider.ram.RamFileSystem;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.webdav4.Webdav4FileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.zip.ZipFileSystemConfigBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

@Configuration
@Slf4j
public class VFSConfig {
    @Bean(name = "fileSystemOptions")
    public FileSystemOptions getFileSystemOptions() throws FileSystemException {
        FileSystemOptions fileSystemOptions = new FileSystemOptions();

        ZipFileSystemConfigBuilder zipBuilder = ZipFileSystemConfigBuilder.getInstance();
        zipBuilder.setCharset(fileSystemOptions, StandardCharsets.UTF_8);

        FtpsFileSystemConfigBuilder ftpsBuilder = FtpsFileSystemConfigBuilder.getInstance();
        ftpsBuilder.setConnectTimeout(fileSystemOptions, Duration.ofSeconds(5));
        ftpsBuilder.setUserDirIsRoot(fileSystemOptions, false);
        ftpsBuilder.setAutodetectUtf8(fileSystemOptions, true);
        ftpsBuilder.setPassiveMode(fileSystemOptions, true);
        ftpsBuilder.setControlEncoding(fileSystemOptions, StandardCharsets.UTF_8.name());

        SftpFileSystemConfigBuilder sftpBuilder = SftpFileSystemConfigBuilder.getInstance();
        sftpBuilder.setConnectTimeout(fileSystemOptions, Duration.ofSeconds(5));
        sftpBuilder.setUserDirIsRoot(fileSystemOptions, false);
        sftpBuilder.setStrictHostKeyChecking(fileSystemOptions, "no");

        Webdav4FileSystemConfigBuilder webdavBuilder = Webdav4FileSystemConfigBuilder.getInstance();
        webdavBuilder.setConnectionTimeout(fileSystemOptions, Duration.ofSeconds(50));
        webdavBuilder.setSoTimeout(fileSystemOptions, Duration.ofSeconds(50));
        webdavBuilder.setMaxConnectionsPerHost(fileSystemOptions, 1000);
        webdavBuilder.setMaxTotalConnections(fileSystemOptions, 1000);
        webdavBuilder.setHostnameVerificationEnabled(fileSystemOptions, false);
        webdavBuilder.setPreemptiveAuth(fileSystemOptions, true);
        webdavBuilder.setUrlCharset(fileSystemOptions, StandardCharsets.UTF_8.name());
        webdavBuilder.setFollowRedirect(fileSystemOptions, true);
        webdavBuilder.setKeepAlive(fileSystemOptions, true);

        return fileSystemOptions;
    }

    @Bean(name = "apacheVfsFileSystemManager")
    public StandardFileSystemManager getStandardFileSystemManager() throws IOException {
        // ????????????BUG??????SpringBoot APP?????????JAR???????????????????????????resource??????
        SpringBootResourceFileProvider resourceFileProvider = new SpringBootResourceFileProvider();

        // ?????? "org/apache/commons/vfs2/impl/providers.xml" ??????
        String providersXmlFileRelativePath = "org/apache/commons/vfs2/impl/providers.xml";
        URL confURL = Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
            .getResource(providersXmlFileRelativePath));

        // ?????? VFS ?????????????????? URL ???????????????
        // ?????? VFS ??????????????? SpringBoot ??? resource URL???
        // ???????????????????????????????????????????????????????????????????????????
        // ?????? ??????????????????????????? ?????????????????????
        // ????????????????????? ??? ???????????? ????????? ????????????
        // ????????? VFS ?????????????????? ??????URL ????????????????????????
        DefaultFileSystemManager tmpFileSystemManager = new DefaultFileSystemManager();
        tmpFileSystemManager.addProvider("ram", new RamFileProvider());
        tmpFileSystemManager.setCacheStrategy(CacheStrategy.ON_RESOLVE);
        tmpFileSystemManager.init();
        FileObject ramFileObject = tmpFileSystemManager.resolveFile("ram:/" + providersXmlFileRelativePath);
        InputStream inputStream = confURL.openStream();
        String xmlContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        inputStream.close();
        inputStream = IOUtils.toInputStream(xmlContent, StandardCharsets.UTF_8);
        OutputStream outputStream = ramFileObject.getContent().getOutputStream();
        outputStream.write(inputStream.readAllBytes());
        outputStream.close();
        inputStream.close();


        // ???????????????????????????
        StandardFileSystemManager fileSystemManager = new StandardFileSystemManager();
        fileSystemManager.setConfiguration(Objects.requireNonNull(ramFileObject.getURL()));
        // ??????????????????????????????????????????
        fileSystemManager.setCacheStrategy(CacheStrategy.ON_RESOLVE);
        fileSystemManager.init();
        fileSystemManager.removeProvider("res");
        fileSystemManager.addProvider("res", resourceFileProvider);
        VFS.setManager(fileSystemManager);

        log.info(fileSystemManager.getClass().getCanonicalName()
            + " is created.Support URL schema: "
            + Arrays.toString(fileSystemManager.getSchemes()));
        tmpFileSystemManager.close();
        return fileSystemManager;
    }

    @Bean(name = "ramFileSystem")
    public RamFileSystem getRamFileSystem() throws IOException {
        FileObject fileObject = getStandardFileSystemManager().resolveFile("ram:/");
        return (RamFileSystem) fileObject.getFileSystem();
    }

    @Bean(name = "vfsWebDavService")
    @ConfigurationProperties(prefix = "app.service.vfs-webdav")
    public VfsWebDavService getVfsWebDavService() throws IOException {
        VfsWebDavService vfsWebDavService = new VfsWebDavService();
        vfsWebDavService.setFileSystemManager(getStandardFileSystemManager());
        vfsWebDavService.setFileSystemOptions(getFileSystemOptions());
        return vfsWebDavService;
    }
}
