package cc.xfl12345.mybigdata.server.initializer;

import cc.xfl12345.mybigdata.server.appconst.JsonSchemaKeyWords;
import cc.xfl12345.mybigdata.server.model.uri.FileURIRelativizeImpl;
import cc.xfl12345.mybigdata.server.model.uri.JarFileURIRelativizeImpl;
import cc.xfl12345.mybigdata.server.model.uri.URIRelativize;
import cc.xfl12345.mybigdata.server.pojo.FileCacheInfoBean;
import cc.xfl12345.mybigdata.server.pojo.ResourceCacheMapBean;
import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.springframework.beans.factory.InitializingBean;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
public class JsonSchemaFileLoader implements InitializingBean {

    protected URI jsonSchemaResourceDirectoryURI = null;
    protected URI ramfsRootJsonSchemaFileURI = null;

    @Getter
    protected URL ramfsRootJsonSchemaFileURL = null;

    @Getter
    protected URI ramfsJsonSchemaDirectoryURI = null;

    @Getter
    @Setter
    protected ResourceCacheMapBean cacheMapBean;

    @Getter
    @Setter
    protected StandardFileSystemManager fileSystemManager;

    @Getter
    @Setter
    protected String jsonSchemaResourceDirectoryRelativePath = "json/schema/";

    @Getter
    @Setter
    protected String jsonSchemaRootFileRelativePath = "spec/2020-12/hyper-schema.json";

    @Getter
    protected URIRelativize resourceUriRelativize;

    @Getter
    protected URIRelativize ramFileUriRelativize = new JarFileURIRelativizeImpl();


    protected String getJsonSchemaRootFileUri(String protocol) {
        return protocol + ":/" + jsonSchemaResourceDirectoryRelativePath + jsonSchemaRootFileRelativePath;
    }

    public String getLoadedResourceRelativePath(URI child) throws URISyntaxException {
        return ramFileUriRelativize.getRelativizeURI(ramfsJsonSchemaDirectoryURI, child);
    }


    // @SuppressWarnings("unchecked")
    protected void copyAndModify(FileObject src, Map<String, String> injectValues) throws IOException, URISyntaxException {
        URI currFileURI = src.getURI();
        URL currFileURL = src.getURL();
        String relativePath = '/' + jsonSchemaResourceDirectoryRelativePath
            + resourceUriRelativize.getRelativizeURI(jsonSchemaResourceDirectoryURI, currFileURI);

        FileObject fileInRamFS = fileSystemManager.resolveFile("ram:/" + relativePath);
        URL currRamFileUrl = fileInRamFS.getURL();
        String currRamFileUrlInString = currRamFileUrl.toString();
        if (src.isFolder()) {
            // ??????????????????
            fileInRamFS.copyFrom(src, Selectors.SELECT_SELF);
        } else {
            // ?????????????????????????????????
            fileInRamFS.getParent().createFolder();
            // ??? JSON
            InputStream inputStream = currFileURL.openStream();
            String jsonString = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            inputStream.close();
            // ?????? JSON
            JSONObject jsonObject = JSONObject.parseObject(jsonString, JSONObject.class);
            jsonObject.putAll(injectValues);
            jsonObject.put(JsonSchemaKeyWords.ID.getName(), currRamFileUrlInString);
            // ?????????
            OutputStream outputStream = fileInRamFS.getContent().getOutputStream();
            outputStream.write(jsonObject.toJSONString().getBytes(StandardCharsets.UTF_8));
            outputStream.close();
        }

        // ??????????????????????????????????????????
        FileCacheInfoBean cacheInfo = cacheMapBean.putIfAbsent(currRamFileUrlInString, new FileCacheInfoBean());
        if (cacheInfo == null) {
            cacheInfo = cacheMapBean.get(currRamFileUrlInString);
        }
        cacheInfo.setOriginURL(currFileURL);
        cacheInfo.addCacheURL(currRamFileUrl);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Start to load JSON schema files to RAM.");
        FileObject jsonSchemaResourceDirectory = fileSystemManager.resolveFile(
            "res:/" + jsonSchemaResourceDirectoryRelativePath
        );
        jsonSchemaResourceDirectoryURI = jsonSchemaResourceDirectory.getURI();

        FileObject jsonSchemaRootFileSource = fileSystemManager.resolveFile(
            getJsonSchemaRootFileUri("res")
        );

        if ("jar".equals(jsonSchemaResourceDirectory.getURL().getProtocol())) {
            resourceUriRelativize = new JarFileURIRelativizeImpl();
        } else {
            resourceUriRelativize = new FileURIRelativizeImpl();
        }

        // ???????????????????????????????????????????????? URI/URL ???????????????
        ramfsJsonSchemaDirectoryURI = fileSystemManager.resolveFile(
            "ram:/" + jsonSchemaResourceDirectoryRelativePath
        ).getURI();
        ramfsRootJsonSchemaFileURL = fileSystemManager.resolveFile(
            getJsonSchemaRootFileUri("ram")
        ).getURL();
        ramfsRootJsonSchemaFileURI = ramfsRootJsonSchemaFileURL.toURI();


        // ?????? ??? JSON Schema ?????? URL
        HashMap<String, String> injectValues = new HashMap<>();
        injectValues.put(
            JsonSchemaKeyWords.SCHEMA.getName(),
            ramfsRootJsonSchemaFileURL.toString()
        );
        copyAndModify(jsonSchemaRootFileSource, injectValues);


        // ???????????????????????????????????????
        Queue<FileObject> queue = new LinkedList<>();
        queue.add(jsonSchemaResourceDirectory);
        while (!queue.isEmpty()) {
            FileObject currFile = queue.remove();
            if (currFile.isFolder()) {
                copyAndModify(currFile, injectValues);
                // ????????????
                queue.addAll(Arrays.stream(currFile.getChildren()).parallel().toList());
            } else {
                copyAndModify(currFile, injectValues);
            }
        }
        log.info("Loading JSON schema files to RAM done.");
    }
}
