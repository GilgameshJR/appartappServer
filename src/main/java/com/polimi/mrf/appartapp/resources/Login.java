package com.polimi.mrf.appartapp.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.polimi.mrf.appartapp.HashGenerator;
import com.polimi.mrf.appartapp.UserAdapter;
import com.polimi.mrf.appartapp.beans.ApartmentSearchServiceBean;
import com.polimi.mrf.appartapp.beans.UserAuthServiceBean;
import com.polimi.mrf.appartapp.beans.UserServiceBean;
import com.polimi.mrf.appartapp.entities.User;
import com.polimi.mrf.appartapp.entities.UserAuthToken;
import com.polimi.mrf.appartapp.google.GoogleTokenVerifier;
import com.polimi.mrf.appartapp.google.GoogleUserInfo;
import org.apache.commons.lang3.RandomStringUtils;

import javax.ejb.EJB;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Date;

@Path("/login")
public class Login {

    @EJB(name = "com.polimi.mrf.appartapp.beans/UserServiceBean")
    UserServiceBean userServiceBean;

    @EJB(name = "com.polimi.mrf.appartapp.beans/UserAuthServiceBean")
    UserAuthServiceBean userAuthServiceBean;

    public static HttpServletResponse appendNewTokenToSession(HttpServletResponse response, UserAuthServiceBean userAuthServiceBean, User user) throws UnsupportedEncodingException {
        UserAuthToken newToken = new UserAuthToken();

        String selector = RandomStringUtils.randomAlphanumeric(12);
        String rawValidator =  RandomStringUtils.randomAlphanumeric(64);

        String hashedValidator = null;

        hashedValidator = HashGenerator.generateSHA256(rawValidator);
        newToken.setSelector(selector);
        newToken.setValidator(hashedValidator);
        newToken.setLastUse(new Date());

        newToken.setUser(user);

        userAuthServiceBean.create(newToken);

        Cookie cookieSelector = new Cookie("selector", selector);
        cookieSelector.setMaxAge(15768000); //6 months

        Cookie cookieValidator = new Cookie("validator", rawValidator);
        cookieValidator.setMaxAge(15768000);

        response.addCookie(cookieSelector);
        response.addCookie(cookieValidator);
        return response;
    }

    @POST
    @Produces("application/json")
    public Response login(@Context HttpServletRequest request, @Context HttpServletResponse response) {

        HttpSession session = request.getSession();


        boolean loggedIn = session != null && session.getAttribute("loggeduser") != null;

        if (loggedIn) {
            User user = (User) session.getAttribute("loggeduser");

            Gson gson = new GsonBuilder()
                    .excludeFieldsWithoutExposeAnnotation()
                    .registerTypeAdapter(User.class, new UserAdapter())
                    .create();
            String json = gson.toJson(user);

            return Response.status(Response.Status.OK).type(MediaType.APPLICATION_JSON).entity(json).build();
        }


        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String idToken = request.getParameter("idtoken");


        if ((email == null || email.isEmpty()) && (password == null || password.isEmpty()) && (idToken == null || idToken.isEmpty())) {

            Cookie[] cookies = request.getCookies();

            if (cookies == null) {
                return Response.status(Response.Status.UNAUTHORIZED).type(MediaType.TEXT_PLAIN).entity("unauthorized").build();
            } else {
                // process auto login for remember me feature
                String selector = "";
                String rawValidator = "";

                for (Cookie aCookie : cookies) {
                    if (aCookie.getName().equals("selector")) {
                        selector = aCookie.getValue();
                    } else if (aCookie.getName().equals("validator")) {
                        rawValidator = aCookie.getValue();
                    }
                }

                if (!"".equals(selector) && !"".equals(rawValidator)) {
                    UserAuthToken token = userAuthServiceBean.findAuthTokenBySelector(selector);

                    if (token == null) {
                        return Response.status(Response.Status.UNAUTHORIZED).type(MediaType.TEXT_PLAIN).entity("unauthorized").build();
                    } else {
                        try {
                            String hashedValidatorDatabase = token.getValidator();
                            String hashedValidatorCookie = HashGenerator.generateSHA256(rawValidator);

                            if (hashedValidatorCookie.equals(hashedValidatorDatabase)) {
                                session = request.getSession();
                                session.setAttribute("loggeduser", token.getUser());

                                // update new token in database
                                String newSelector = RandomStringUtils.randomAlphanumeric(12);
                                String newRawValidator = RandomStringUtils.randomAlphanumeric(64);

                                String newHashedValidator = HashGenerator.generateSHA256(newRawValidator);

                                token.setSelector(newSelector);
                                token.setValidator(newHashedValidator);
                                token.setLastUse(new Date());
                                userAuthServiceBean.update(token);

                                // update cookie
                                Cookie cookieSelector = new Cookie("selector", newSelector);
                                cookieSelector.setMaxAge(604800);

                                Cookie cookieValidator = new Cookie("validator", newRawValidator);
                                cookieValidator.setMaxAge(604800);

                                response.addCookie(cookieSelector);
                                response.addCookie(cookieValidator);

                                Gson gson = new GsonBuilder()
                                        .excludeFieldsWithoutExposeAnnotation()
                                        .registerTypeAdapter(User.class, new UserAdapter())
                                        .create();
                                String json = gson.toJson(token.getUser());

                                return Response.status(Response.Status.OK).type(MediaType.APPLICATION_JSON).entity(json).build();
                            } else {
                                    return Response.status(Response.Status.UNAUTHORIZED).type(MediaType.TEXT_PLAIN).entity("unauthorized").build();
                            }
                        } catch (java.io.UnsupportedEncodingException e) {
                            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.TEXT_PLAIN).entity("INTERNAL_SERVER_ERROR").build();
                        }
                    }
                } else {
                    return Response.status(Response.Status.UNAUTHORIZED).type(MediaType.TEXT_PLAIN).entity("unauthorized").build();
                }
            }
        } else {
            User user=null;

            //validate cred
            if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
                //google auth
                return Response.status(Response.Status.UNAUTHORIZED).type(MediaType.TEXT_PLAIN).entity("unauthorized").build();


            } else {
                user = userServiceBean.getUser(email, password);
            }

            if (user == null)
                return Response.status(Response.Status.UNAUTHORIZED).type(MediaType.TEXT_PLAIN).entity("unauthorized").build();
            else {
                Cookie[] cookies = request.getCookies();

                if (cookies==null) {
                    //create new entity
                    try {
                        response=appendNewTokenToSession(response, userAuthServiceBean, user);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.TEXT_PLAIN).entity("INTERNAL_SERVER_ERROR").build();
                    }
                } else {

                    String selector = "";
                    String rawValidator = "";

                    for (Cookie aCookie : cookies) {
                        if (aCookie.getName().equals("selector")) {
                            selector = aCookie.getValue();
                        } else if (aCookie.getName().equals("validator")) {
                            rawValidator = aCookie.getValue();
                        }
                    }

                    if (!"".equals(selector) && !"".equals(rawValidator)) {
                        UserAuthToken token = userAuthServiceBean.findAuthTokenBySelector(selector);

                        if (token == null) {
                            //create new entity
                            try {
                                response=appendNewTokenToSession(response, userAuthServiceBean, user);
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.TEXT_PLAIN).entity("INTERNAL_SERVER_ERROR").build();
                            }
                        } else {
                            try {
                                String hashedValidatorDatabase = token.getValidator();
                                String hashedValidatorCookie = HashGenerator.generateSHA256(rawValidator);

                                if (hashedValidatorCookie.equals(hashedValidatorDatabase)) {
                                    // update new token in database
                                    String newSelector = RandomStringUtils.randomAlphanumeric(12);
                                    String newRawValidator = RandomStringUtils.randomAlphanumeric(64);

                                    String newHashedValidator = HashGenerator.generateSHA256(newRawValidator);

                                    token.setSelector(newSelector);
                                    token.setValidator(newHashedValidator);
                                    token.setLastUse(new Date());
                                    userAuthServiceBean.update(token);

                                    // update cookie
                                    Cookie cookieSelector = new Cookie("selector", newSelector);
                                    cookieSelector.setMaxAge(604800);

                                    Cookie cookieValidator = new Cookie("validator", newRawValidator);
                                    cookieValidator.setMaxAge(604800);

                                    response.addCookie(cookieSelector);
                                    response.addCookie(cookieValidator);
                                } else {
                                    //create new entity
                                    try {
                                        response=appendNewTokenToSession(response, userAuthServiceBean, user);
                                    } catch (UnsupportedEncodingException e) {
                                        e.printStackTrace();
                                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.TEXT_PLAIN).entity("INTERNAL_SERVER_ERROR").build();
                                    }
                                }
                            } catch (java.io.UnsupportedEncodingException e) {
                                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.TEXT_PLAIN).entity("INTERNAL_SERVER_ERROR").build();
                            }
                        }
                    } else {
                        //create new entity
                        try {
                            response=appendNewTokenToSession(response, userAuthServiceBean, user);
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.TEXT_PLAIN).entity("INTERNAL_SERVER_ERROR").build();
                        }
                    }
                }

                Gson gson = new GsonBuilder()
                        .excludeFieldsWithoutExposeAnnotation()
                        .registerTypeAdapter(User.class, new UserAdapter())
                        .create();
                String json = gson.toJson(user);

                return Response.status(Response.Status.OK).type(MediaType.APPLICATION_JSON).entity(json).build();
            }
        }
    }
}