package org.openbase.bco.psc.lib.jp;

/*
 * -
 * #%L
 * BCO PSC Library
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.openbase.jps.exception.JPBadArgumentException;
import org.openbase.jps.exception.JPNotAvailableException;
import org.openbase.jps.preset.AbstractJPDouble;
import org.openbase.jps.preset.AbstractJPInteger;
import org.openbase.jps.preset.AbstractJPLong;

import java.util.List;

/**
 * JavaProperty used to specify a timeout in milliseconds.
 *
 * @author <a href="mailto:dreinsch@techfak.uni-bielefeld.de">Dennis Reinsch</a>
 */
public abstract class AbstractJPTimeout extends AbstractJPLong {

    public AbstractJPTimeout(String[] commandIdentifier) {
        super(commandIdentifier);
    }

    /**
     * {@inheritDoc}
     *
     * @param arguments {@inheritDoc}
     * @return {@inheritDoc}
     * @throws JPBadArgumentException {@inheritDoc}
     */
    @Override
    protected Long parse(List<String> arguments) throws JPBadArgumentException {
        Long i = super.parse(arguments);
        if (i < 0.0) {
            throw new JPBadArgumentException("Timeout needs to be positive!");
        }
        return i;
    }
}