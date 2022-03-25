package com.polimi.mrf.appartapp.resources;

import com.google.gson.Gson;
import com.polimi.mrf.appartapp.beans.UserServiceBean;
import com.polimi.mrf.appartapp.entities.User;

import javax.ejb.EJB;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

@Path("/login")
public class LoginResource {
    @EJB(name = "com.polimi.mrf.appartapp.beans/UserServiceBean")
    UserServiceBean userServiceBean;

    @POST
    @Produces("application/json")
    public Response login(@Context HttpServletRequest request) {
        String email=request.getParameter("email");
        String password=request.getParameter("password");

        if (email==null || password==null)
            return Response.status(Response.Status.UNAUTHORIZED).build();

        User user=userServiceBean.getUser(email, password);
        if (user==null)
            return Response.status(Response.Status.UNAUTHORIZED).build();
        else
            return Response.status(Response.Status.OK).entity(new Gson().toJson(user)).build();
    }
}