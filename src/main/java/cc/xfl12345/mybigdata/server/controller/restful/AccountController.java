package cc.xfl12345.mybigdata.server.controller.restful;

import cc.xfl12345.mybigdata.server.appconst.api.result.JsonApiResult;
import cc.xfl12345.mybigdata.server.appconst.api.result.LoginApiResult;
import cc.xfl12345.mybigdata.server.model.api.response.JsonCommonApiResponseObject;
import cc.xfl12345.mybigdata.server.service.AccountService;
import cc.xfl12345.mybigdata.server.utility.ApiResultUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping(AccountController.servletName)
public class AccountController {

    @Getter
    protected AccountService accountService;

    @Autowired
    public void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }

    public static final String servletName = "user";
    public static final String servletPathCache1 = "/" + servletName;
    public static final String servletPathCache2 = "/" + servletName + "/";

    // @RequestMapping(path = {"", "index", "index.html"})
    // public void indexView(HttpServletResponse response) throws IOException {
    //
    //     if (accountService.checkIsLoggedIn()) {
    //
    //     }
    //
    // }


    @PostMapping("reset-password")
    public JsonCommonApiResponseObject resetPassword(String passwordHash) {
        JsonCommonApiResponseObject responseObject = new JsonCommonApiResponseObject(
            accountService.getJsonApiVersion()
        );
        boolean apiResult = accountService.resetPassword(passwordHash);
        if (apiResult) {
            responseObject.setApiResult(JsonApiResult.SUCCEED);
        } else {
            responseObject.setApiResult(JsonApiResult.FAILED);
        }

        return responseObject;
    }

    @PostMapping("login")
    public JsonCommonApiResponseObject login(String username, String passwordHash) {
        JsonCommonApiResponseObject responseObject = new JsonCommonApiResponseObject(
            accountService.getJsonApiVersion()
        );
        LoginApiResult loginApiResult = accountService.login(username, passwordHash);
        ApiResultUtil.convert(loginApiResult, responseObject);

        return responseObject;
    }


    @PostMapping("logout")
    public JsonCommonApiResponseObject logout() {
        JsonCommonApiResponseObject responseObject = new JsonCommonApiResponseObject(
            accountService.getJsonApiVersion()
        );
        accountService.logout();
        responseObject.setApiResult(JsonApiResult.SUCCEED);

        return responseObject;
    }


}
