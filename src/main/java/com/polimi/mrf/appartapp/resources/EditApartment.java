package com.polimi.mrf.appartapp.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.polimi.mrf.appartapp.UserAdapter;
import com.polimi.mrf.appartapp.beans.ApartmentServiceBean;
import com.polimi.mrf.appartapp.entities.Apartment;
import com.polimi.mrf.appartapp.entities.CredentialsUser;
import com.polimi.mrf.appartapp.entities.GoogleUser;
import com.polimi.mrf.appartapp.entities.User;

import javax.ejb.EJB;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/reserved/editapartment")
public class EditApartment {
    @EJB(name = "com.polimi.mrf.appartapp.beans/ApartmentServiceBean")
    ApartmentServiceBean apartmentServiceBean;

    @POST
    @Produces("application/json")
    public Response EditApartment(@Context HttpServletRequest request) {
        String listingTitle=request.getParameter("listingtitle");
        String description=request.getParameter("description");
        String address=request.getParameter("address");
        String additionalExpenseDetail=request.getParameter("additionalexpensedetail");
        String priceStr=request.getParameter("price");

        Long id=Long.parseLong(request.getParameter("id"));
        Apartment apartment=apartmentServiceBean.getApartment(id);

        User user= (User) request.getAttribute("user");

        if (Long.compare(apartment.getOwner().getId(), user.getId())==0) { //if logged user is the owner of apartment

            if (listingTitle!=null && listingTitle.length()>0)
                apartment.setListingTitle(listingTitle.trim());

            if (description!=null && description.length()>0)
                apartment.setDescription(description.trim());

            if (address!=null && address.length()>0)
                apartment.setAddress(address.trim());

            if (additionalExpenseDetail!=null && additionalExpenseDetail.length()>0)
                apartment.setAdditionalExpenseDetail(additionalExpenseDetail.trim());

            if (priceStr!=null && priceStr.length()>0)
                apartment.setPrice(Integer.parseInt(priceStr.trim()));

            apartmentServiceBean.updateApartment(apartment);

            UserAdapter userAdapter=new UserAdapter();
            Gson gson = new GsonBuilder()
                    .excludeFieldsWithoutExposeAnnotation()
                    .registerTypeAdapter(User.class, userAdapter)
                    .registerTypeAdapter(CredentialsUser.class, userAdapter)
                    .registerTypeAdapter(GoogleUser.class, userAdapter)
                    .create();
            String json=gson.toJson(apartment);

            return Response.status(Response.Status.OK).type(MediaType.APPLICATION_JSON).entity(json).build();
        } else {
            return Response.status(Response.Status.UNAUTHORIZED.getStatusCode()).build();
        }
    }
}
