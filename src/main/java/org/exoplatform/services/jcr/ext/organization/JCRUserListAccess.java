/*
 * Copyright (C) 2003-2009 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.services.jcr.ext.organization;

import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.services.organization.User;

import javax.jcr.Session;

/**
 * Created by The eXo Platform SAS.
 * 
 * @author <a href="mailto:anatoliy.bazko@exoplatform.com.ua">Anatoliy Bazko</a>
 * @version $Id: MyResourceAccess.java 111 2008-11-11 11:11:11Z $
 */
public abstract class JCRUserListAccess implements ListAccess<User>
{

   protected final JCROrganizationServiceImpl service;

   protected final Utils utils;

   protected final UserHandlerImpl uHandler;

   /**
    * JCRUserListAccess constructor.
    */
   public JCRUserListAccess(JCROrganizationServiceImpl service)
   {
      this.service = service;
      this.uHandler = (UserHandlerImpl)service.getUserHandler();
      this.utils = new Utils(service);
   }

   /**
    * {@inheritDoc}
    */
   public User[] load(int index, int length) throws Exception, IllegalArgumentException
   {
      Session session = service.getStorageSession();
      try
      {
         return load(session, index, length);
      }
      finally
      {
         session.logout();
      }
   }

   /**
    * {@inheritDoc}
    */
   public int getSize() throws Exception
   {
      Session session = service.getStorageSession();
      try
      {
         return getSize(session);
      }
      finally
      {
         session.logout();
      }
   }

   /**
    * Loads users into array.
    * 
    * @param session
    *          The current session
    * @param index
    *          Offset
    * @param length
    *          Number of users
    * @return result array of users
    * @throws Exception
    *           if any error occurred
    */
   protected abstract User[] load(Session session, int index, int length) throws Exception;

   /**
    * Determines the count of available users.
    * 
    * @param session
    *          The current session
    * @return list size
    * @throws Exception
    *           if any error occurrred
    */
   protected abstract int getSize(Session session) throws Exception;

}
