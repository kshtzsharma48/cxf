/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.systest.aegis;

import java.util.HashMap;
import java.util.Map;

import javax.jws.WebService;

import org.apache.cxf.feature.Features;
import org.apache.cxf.systest.aegis.bean.AddItemWrapper;

@Features(features = "org.apache.cxf.feature.LoggingFeature")
@WebService (endpointInterface = "org.apache.cxf.systest.aegis.AegisJaxWs")
public class AegisJaxWsImpl implements AegisJaxWs {
    
    Map<Integer, AddItemWrapper> items = new HashMap<Integer, AddItemWrapper>();

    public void addItem(AddItemWrapper item) {
        items.put(item.getKey(), item);
    }

    public Map getItemsMap() {
        return items;
    }

    public Map<Integer, AddItemWrapper> getItemsMapSpecified() {
        return items;
    }

    public AddItemWrapper getItemByKey(String key1, String key2) {
        AddItemWrapper fake = new AddItemWrapper();
        fake.setKey(new Integer(33));
        fake.setData("and a third");
        return fake;
    }
    
    

}
