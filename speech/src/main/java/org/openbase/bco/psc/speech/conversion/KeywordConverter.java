package org.openbase.bco.psc.speech.conversion;

/*-
 * #%L
 * BCO PSC Speech
 * %%
 * Copyright (C) 2016 - 2019 openbase.org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.slf4j.LoggerFactory;
import com.google.protobuf.Message;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;


public class KeywordConverter {
    /**
     * Logger instance.
     */
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(KeywordConverter.class);

    private HashMap<String, Message> keywordServiceMap;

    public KeywordConverter(String fileName) throws IOException, ClassNotFoundException {
        File file = new File(fileName);
        ObjectInputStream input = new ObjectInputStream(new GZIPInputStream(new FileInputStream(file)));

        Object readObject = input.readObject();
        input.close();

        if (!(readObject instanceof HashMap)) throw new IOException("Data is not a hashmap");

        keywordServiceMap = (HashMap<String, Message>) readObject;

        for (String key : keywordServiceMap.keySet()) {
            LOGGER.info(key + ": " + keywordServiceMap.get(key));
        }
    }

    public ArrayList<Message> getMessages(String[] keywords) {
        ArrayList<Message> messages = new ArrayList<>();

        for (String kw : keywords) {
            if (keywordServiceMap.containsValue(kw)) {

                Message event = keywordServiceMap.get(kw);
                messages.add(event);
                LOGGER.info("Keyword detected:" + kw + " corresponding event: " + event);
            }
        }
        return messages;
    }
}