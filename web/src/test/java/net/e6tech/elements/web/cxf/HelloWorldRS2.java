/*
 * Copyright 2015-2019 Futeh Kao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.e6tech.elements.web.cxf;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/v1/hello2")
public class HelloWorldRS2 {

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public String sayHi( @QueryParam("text") String text) {
        return "Hello2 " + text;
    }

    @PUT
    @Produces({MediaType.APPLICATION_JSON})
    public void putMethod(@QueryParam("ext") String text, PutData data) {
        System.out.println(text + " " + data);
    }
}
