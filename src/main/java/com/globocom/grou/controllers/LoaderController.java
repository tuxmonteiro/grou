/*
 * Copyright (c) 2017-2017 Globo.com
 * All rights reserved.
 *
 * This source is subject to the Apache License, Version 2.0.
 * Please see the LICENSE file for more information.
 *
 * Authors: See AUTHORS file
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.globocom.grou.controllers;

import com.globocom.grou.entities.Loader;
import com.globocom.grou.entities.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class LoaderController {

    private final StringRedisTemplate redisTemplate;

    @Autowired
    LoaderController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/loaders")
    public List<Loader> loaders() {
        return redisTemplate.keys("grou:loader:*").stream().map(k -> {
                    String loaderName = k.split(":")[2];
                    String loaderStatusStr = redisTemplate.opsForValue().get(k);
                    Loader loader = new Loader();
                    loader.setName(loaderName);
                    loader.setStatus(Enum.valueOf(Test.Status.class, loaderStatusStr));
                    return loader;
                }).collect(Collectors.toList());
    }
}