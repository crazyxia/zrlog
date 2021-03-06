package com.fzb.blog.web.handler;

import com.fzb.blog.util.ZrlogUtil;
import com.fzb.blog.web.util.WebTools;
import com.fzb.common.util.IOUtil;
import com.fzb.common.util.http.HttpUtil;
import com.fzb.common.util.http.handle.CloseResponseHandle;
import com.jfinal.handler.Handler;
import com.jfinal.kit.PathKit;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * 用于对静态文件的请求的检查，和静态化文章页，加快文章页的响应。
 */
public class StaticFileCheckHandler extends Handler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaticFileCheckHandler.class);

    //不希望部分技术人走后门，拦截一些不合法的请求
    private static final Set<String> FORBIDDEN_URI_EXT_SET = new HashSet<String>();

    static {
        //由于程序的.jsp文件没有存放在WEB-INF目录，为了防止访问.jsp页面获得的没有数据的页面，或则是错误的页面。
        FORBIDDEN_URI_EXT_SET.add(".jsp");
        //这主要用在主题目录下面的配置文件。
        FORBIDDEN_URI_EXT_SET.add(".properties");
    }

    public void handle(String target, HttpServletRequest request,
                       HttpServletResponse response, boolean[] isHandled) {
        String ext = null;
        if (target.contains("/")) {
            String name = target.substring(target.lastIndexOf('/'));
            if (name.contains(".")) {
                ext = name.substring(name.lastIndexOf('.'));
            }
        }
        if (ext != null) {
            if (!FORBIDDEN_URI_EXT_SET.contains(ext)) {
                // 处理静态化文件,仅仅缓存文章页(变化较小)
                if (target.endsWith(".html") && target.startsWith("/post/")) {
                    File htmlFile = new File(PathKit.getWebRootPath()
                            + request.getServletPath());
                    if (!htmlFile.exists()) {
                        String tempTarget = target.substring(0,
                                target.lastIndexOf('.'));
                        String home = WebTools.getRealScheme(request) + "://"
                                + request.getHeader("host")
                                + request.getContextPath() + tempTarget;
                        target = tempTarget;
                        if (!ZrlogUtil.isStaticBlogPlugin(request)) {
                            convert2Html(home, htmlFile);
                        }
                    }
                    this.next.handle(target, request, response, isHandled);
                } else {
                    this.next.handle(target, request, response, isHandled);
                }
            } else {
                try {
                    //非法请求, 返回403
                    request.getSession();
                    response.sendError(403);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            this.next.handle(target, request, response, isHandled);
        }

    }

    /**
     * 将一个网页转化对应文件，用于静态化文章页
     *
     * @param sSourceUrl
     * @param file
     */
    private void convert2Html(String sSourceUrl, File file) {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }
        try {
            CloseableHttpResponse closeableHttpResponse = HttpUtil.getInstance().sendGetRequest(sSourceUrl, new CloseResponseHandle(), new HashMap<String, String>()).getT();
            if (closeableHttpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                String str = IOUtil.getStringInputStream(closeableHttpResponse.getEntity().getContent());
                if (str != null) {
                    IOUtil.writeBytesToFile(str.getBytes("UTF-8"), file);
                }
            }
        } catch (IOException e) {
            LOGGER.error("convert2Html error", e);
        }
    }
}
