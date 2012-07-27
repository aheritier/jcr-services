/*
 * Copyright (C) 2003-2012 eXo Platform SAS.
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

import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFormatException;

/**
 * Created by The eXo Platform SAS.
 * 
 * Date: 04.07.2012
 * 
 * @author <a href="mailto:dvishinskiy@exoplatform.com">Dmitriy Vishinskiy</a>
 * @version $Id: JCROrganizationServiceMigration.java 76870 2012-07-04 10:38:54Z dkuleshov $
 */

public class MigrationTool
{
   /**
    * JCROrganizationServiceImpl instance.
    */
   private JCROrganizationServiceImpl service;

   /**
    * Path where old structure will be moved.
    */
   private String oldStoragePath;

   /**
    * Path in old structure where user nodes stored.
    */
   private String usersStorageOld;

   /**
    * Path in old structure where group nodes stored.
    */
   private String groupsStorageOld;

   /**
    * Path in old structure where membershipTypes nodes stored.
    */
   private String membershipTypesStorageOld;

   /**
    * The child node of user node where membership is stored (old structure).
    */
   public static final String JOS_USER_MEMBERSHIP = "jos:userMembership";

   /**
    * The property of user node where group node uuid is stored (old structure).
    */
   public static final String JOS_GROUP = "jos:group";

   /**
    * The child node of user node where attributes is stored (old structure).
    */
   public static final String JOS_ATTRIBUTES = "jos:attributes";

   /**
    * The nodetype of old organization structure root node.
    */
   public static final String JOS_ORGANIZATION_NODETYPE_OLD = "jos:organizationStorage";

   /**
    * The nodetype of old organization structure users storage node.
    */
   public static final String JOS_USERS_NODETYPE_OLD = "jos:organizationUsers";

   /**
    * The property of a group node where parent id is stored (old structure).
    */
   public static final String JOS_PARENT_ID = "jos:parentId";

   /**
    * The property of a membership node where group id is stored (old structure).
    */
   public static final String JOS_GROUP_ID = "jos:groupId";

   private static final String MIGRATION_STATUS_NODE = "migration00Data00Temp";

   private static final String STATUS_DATA_MOVED = "mig-dataMoved";

   private static final String STATUS_NEW_STRUCTURE_CREATED = "mig-newStructureCreated";

   private static final String STATUS_GROUPS_MIGRATED = "mig-groupsMigrated";

   private static final String STATUS_MEMBERSHIP_TYPES_MIGRATED = "mig-membershipTypesMigrated";

   private static final String STATUS_USERS_MIGRATED = "mig-usersMigrated";

   private static final String STATUS_USER_PROFILES_MIGRATED = "mig-userProfilesMigrated";

   private static final String STATUS_USER_MEMBERSHIPS_MIGRATED = "mig-userMembershipsMigrated";

   private static final String STATUS_OLD_STRUCTURE_REMOVED = "mig-oldStructureRemoved";

   /**
    * Logger.
    */
   protected static final Log LOG = ExoLogger.getLogger("exo-jcr-services.MigrationTool");

   /**
    * MigrationTool constructor.
    */
   MigrationTool(JCROrganizationServiceImpl service)
   {
      this.service = service;
   }

   /**
    * Method that aggregates all needed migration operations in needed order.
    * @throws RepositoryException if error occurs.
    */
   void migrate() throws RepositoryException
   {
      try
      {
         if (LOG.isInfoEnabled())
         {
            LOG.info("Migration started.");
         }

         prepareStructure();
         migrateData();
         cleanupStructure();

         if (LOG.isInfoEnabled())
         {
            LOG.info("Migration completed.");
         }
      }
      catch (Exception e)
      {
         throw new RepositoryException("Migration failed", e);
      }

   }

   /**
    * Method that checks if migration is needed.
    * @return true if migration is needed false otherwise.
    * @throws RepositoryException if error occurs.
    */
   boolean migrationRequired() throws RepositoryException
   {
      Session session = service.getStorageSession();
      oldStoragePath = service.getStoragePath() + "-old";
      usersStorageOld = oldStoragePath + "/" + JCROrganizationServiceImpl.STORAGE_JOS_USERS;
      groupsStorageOld = oldStoragePath + "/" + JCROrganizationServiceImpl.STORAGE_JOS_GROUPS;
      membershipTypesStorageOld = oldStoragePath + "/" + JCROrganizationServiceImpl.STORAGE_JOS_MEMBERSHIP_TYPES;

      try
      {

         return session.itemExists(MIGRATION_STATUS_NODE)
            || ((Node)session.getItem(service.getStoragePath())).isNodeType(JOS_ORGANIZATION_NODETYPE_OLD);
      }
      catch (PathNotFoundException e)
      {
         //Means that database is empty. No migration needed.
         return false;
      }
      finally
      {
         session.logout();
      }
   }

   /**
    * Prepares structure for migration. 
    * Renames old structure node and creates new structure compatible with new version of organization service.
    * @throws RepositoryException if error occurs.
    */
   private void prepareStructure() throws RepositoryException
   {
      ExtendedSession session = (ExtendedSession)service.getStorageSession();
      try
      {
         HashMap<String, Boolean> status = readStatus();

         if (!status.get(STATUS_DATA_MOVED))
         {
            session.rename(service.getStoragePath(), oldStoragePath);
            session.save();
            status.put(STATUS_DATA_MOVED, true);
            writeStatus(status);
         }

         if (!status.get(STATUS_NEW_STRUCTURE_CREATED))
         {
            service.createStructure();
            status.put(STATUS_NEW_STRUCTURE_CREATED, true);
            writeStatus(status);
         }

      }
      finally
      {
         session.logout();
      }
   }

   /**
    * Method that responsible for migration itself.
    * @throws Exception if error occurs.
    */
   private void migrateData() throws Exception
   {
      HashMap<String, Boolean> status = readStatus();

      if (!status.get(STATUS_GROUPS_MIGRATED))
      {
         migrateGroups();
         status.put(STATUS_GROUPS_MIGRATED, true);
         writeStatus(status);
      }

      if (!status.get(STATUS_MEMBERSHIP_TYPES_MIGRATED))
      {
         migrateMembershipTypes();
         status.put(STATUS_MEMBERSHIP_TYPES_MIGRATED, true);
         writeStatus(status);
      }

      if (!status.get(STATUS_USERS_MIGRATED))
      {
         migrateUsers();
         status.put(STATUS_USERS_MIGRATED, true);
         writeStatus(status);
      }

      if (!status.get(STATUS_USER_PROFILES_MIGRATED))
      {
         migrateProfiles();
         status.put(STATUS_USER_PROFILES_MIGRATED, true);
         writeStatus(status);
      }

      if (!status.get(STATUS_USER_MEMBERSHIPS_MIGRATED))
      {
         migrateMemberships();
         status.put(STATUS_USER_MEMBERSHIPS_MIGRATED, true);
         writeStatus(status);
      }
   }

   /**
    * Method that responsible for groups migration.
    * @throws Exception if error occurs.
    */
   private void migrateGroups() throws Exception
   {
      Session session = service.getStorageSession();
      try
      {
         if (session.itemExists(groupsStorageOld))
         {
            NodeIterator iterator = ((ExtendedNode)session.getItem(groupsStorageOld)).getNodesLazily();
            GroupHandlerImpl gh = ((GroupHandlerImpl)service.getGroupHandler());
            while (iterator.hasNext())
            {
               Node oldGroupNode = iterator.nextNode();
               gh.migrateGroup(oldGroupNode);

               migrateGroups(oldGroupNode);
            }
         }
      }
      finally
      {
         session.logout();
      }
   }

   /**
    * Method that responsible for groups migration.
    * @param startNode is for tree traversal.
    * @throws Exception if error occurs.
    */
   private void migrateGroups(Node startNode) throws Exception
   {
      NodeIterator iterator = ((ExtendedNode)startNode).getNodesLazily();
      GroupHandlerImpl gh = ((GroupHandlerImpl)service.getGroupHandler());
      while (iterator.hasNext())
      {
         Node oldGroupNode = iterator.nextNode();
         gh.migrateGroup(oldGroupNode);

         migrateGroups(oldGroupNode);
      }
   }

   /**
    * Method that responsible for membershipTypes migration.
    * @throws Exception if error occurs.
    */
   private void migrateMembershipTypes() throws Exception
   {
      Session session = service.getStorageSession();
      try
      {
         if (session.itemExists(membershipTypesStorageOld))
         {
            NodeIterator iterator = ((ExtendedNode)session.getItem(membershipTypesStorageOld)).getNodesLazily();
            MembershipTypeHandlerImpl mth = ((MembershipTypeHandlerImpl)service.getMembershipTypeHandler());
            while (iterator.hasNext())
            {
               Node oldTypeNode = iterator.nextNode();
               mth.migrateMembershipType(oldTypeNode);
            }
         }
      }
      finally
      {
         session.logout();
      }
   }

   /**
    * Method that responsible for users migration.
    * @throws Exception if error occurs.
    */
   private void migrateUsers() throws Exception
   {
      Session session = service.getStorageSession();
      try
      {
         if (session.itemExists(usersStorageOld))
         {
            NodeIterator iterator = ((ExtendedNode)session.getItem(usersStorageOld)).getNodesLazily();
            UserHandlerImpl uh = ((UserHandlerImpl)service.getUserHandler());
            while (iterator.hasNext())
            {
               uh.migrateUser(iterator.nextNode());
            }
         }
      }
      finally
      {
         session.logout();
      }
   }

   /**
    * Method that responsible for userProfiles migration.
    * @throws Exception if error occurs.
    */
   private void migrateProfiles() throws Exception
   {
      Session session = service.getStorageSession();
      try
      {
         if (session.itemExists(usersStorageOld))
         {
            NodeIterator iterator = ((ExtendedNode)session.getItem(usersStorageOld)).getNodesLazily();
            UserProfileHandlerImpl uph = ((UserProfileHandlerImpl)service.getUserProfileHandler());
            while (iterator.hasNext())
            {
               Node oldUserNode = iterator.nextNode();
               uph.migrateProfile(oldUserNode);
            }
         }
      }
      finally
      {
         session.logout();
      }
   }

   /**
    * Method that responsible for userMemberships migration.
    * @throws Exception if error occurs.
    */
   private void migrateMemberships() throws Exception
   {
      Session session = service.getStorageSession();
      try
      {
         if (session.itemExists(usersStorageOld))
         {
            NodeIterator iterator = ((ExtendedNode)session.getItem(usersStorageOld)).getNodesLazily();
            MembershipHandlerImpl mh = ((MembershipHandlerImpl)service.getMembershipHandler());
            while (iterator.hasNext())
            {
               Node oldUserNode = iterator.nextNode();
               mh.migrateMemberships(oldUserNode);
               oldUserNode.remove();
               session.save();
            }
         }
      }
      finally
      {
         session.logout();
      }
   }

   /**
    * Method that makes cleanup after migration.
    * @throws RepositoryException if error occurs.
    */
   private void cleanupStructure() throws RepositoryException
   {
      Session session = (ExtendedSession)service.getStorageSession();
      try
      {
         HashMap<String, Boolean> status = readStatus();

         if (!status.get(STATUS_OLD_STRUCTURE_REMOVED))
         {
            NodeIterator usersIter = ((ExtendedNode)session.getItem(usersStorageOld)).getNodesLazily();
            while (usersIter.hasNext())
            {
               Node currentUser = usersIter.nextNode();
               currentUser.remove();
               session.save();
            }

            NodeIterator groupsIter = ((ExtendedNode)session.getItem(groupsStorageOld)).getNodesLazily();
            while (groupsIter.hasNext())
            {
               Node currentGroup = groupsIter.nextNode();
               currentGroup.remove();
               session.save();
            }

            NodeIterator membershipTypesIter =
               ((ExtendedNode)session.getItem(membershipTypesStorageOld)).getNodesLazily();
            while (membershipTypesIter.hasNext())
            {
               Node currentMembershipType = membershipTypesIter.nextNode();
               currentMembershipType.remove();
               session.save();
            }

            session.getItem(oldStoragePath).remove();
            session.save();

            status.put(STATUS_OLD_STRUCTURE_REMOVED, true);
            writeStatus(status);
         }

         if (session.itemExists(MIGRATION_STATUS_NODE))
         {
            session.getItem(MIGRATION_STATUS_NODE).remove();
            session.save();
         }
      }
      finally
      {
         session.logout();
      }
   }

   /**
    * Method that reads migration status.
    * Creates migration status node if it does not exists.
    * @return migration status as HashMap
    * @throws RepositoryException if error occurs.
    */
   private HashMap<String, Boolean> readStatus() throws RepositoryException
   {
      Session session = service.getStorageSession();
      try
      {
         if (!session.itemExists(MIGRATION_STATUS_NODE))
         {
            session.getRootNode().addNode(MIGRATION_STATUS_NODE, "nt:unstructured");
            session.save();
            createStatusNode();
         }

         HashMap<String, Boolean> status = new HashMap<String, Boolean>();
         PropertyIterator prIter = ((Node)session.getItem("/" + MIGRATION_STATUS_NODE)).getProperties();
         while (prIter.hasNext())
         {
            Property prop = prIter.nextProperty();
            try
            {
               if (prop.getName().startsWith("mig-"))
               {
                  status.put(prop.getName(), prop.getBoolean());
               }
            }
            catch (ValueFormatException e)
            {
               //Impossible behavior.
               status.put(prop.getName(), false);
            }
         }
         return status;
      }
      finally
      {
         session.logout();
      }
   }

   /**
    * Method that writes migration status.
    * @param status is a migration status as HashMap
    * @throws RepositoryException if error occurs.
    */
   private void writeStatus(Map<String, Boolean> status) throws RepositoryException
   {
      Session session = service.getStorageSession();
      try
      {
         Set<String> keys = status.keySet();
         Node statusNode = (Node)session.getItem("/" + MIGRATION_STATUS_NODE);
         for (String key : keys)
         {
            statusNode.setProperty(key, status.get(key));
         }
         session.save();
      }
      finally
      {
         session.logout();
      }
   }

   /**
    * Method that responsible for creation of status node.
    * @throws RepositoryException if error occurs.
    */
   private void createStatusNode() throws RepositoryException
   {
      Map<String, Boolean> status = new HashMap<String, Boolean>();
      status.put(STATUS_DATA_MOVED, false);

      status.put(STATUS_NEW_STRUCTURE_CREATED, false);

      status.put(STATUS_GROUPS_MIGRATED, false);
      status.put(STATUS_MEMBERSHIP_TYPES_MIGRATED, false);
      status.put(STATUS_USERS_MIGRATED, false);
      status.put(STATUS_USER_PROFILES_MIGRATED, false);
      status.put(STATUS_USER_MEMBERSHIPS_MIGRATED, false);

      status.put(STATUS_OLD_STRUCTURE_REMOVED, false);
      writeStatus(status);
   }
}
