package io.coredge.health.claims.service;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.ReviewPriority;
import io.coredge.health.claims.db.entity.UserBucket;
import io.coredge.health.claims.db.entity.permission.Permission;
import io.coredge.health.claims.db.entity.permission.User;
import io.coredge.health.claims.db.repositories.InsuranceClaimCustomRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.ReviewPriorityRepository;
import io.coredge.health.claims.db.repositories.UserBucketRepository;
import io.coredge.health.claims.db.repositories.permission.PermissionRepository;
import io.coredge.health.claims.db.repositories.permission.UserRepository;
import io.coredge.health.claims.enums.*;
import io.coredge.health.claims.enums.permission.ActionEnum;
import io.coredge.health.claims.enums.permission.ResourceEnum;
import io.coredge.health.claims.exception.app.*;
import io.coredge.health.claims.mapper.UserAssignmentMapper;
import io.coredge.health.claims.models.assignments.*;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.utils.ClaimsValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserAssignmentService {

    @Value("#{'${task.list.allowed.resources:Preauthorization,Claim}'.split(',')}")
    private List<String> listOfAllowedResources;

    @Value("${task.list.allowed.limit:10}")
    private Long taskLimit;

    @Value("${task.list.restricted.code}.split(',')")
    private List<String> restrictedCodes;

    @Value("${task.list.restricted.minimumClaimAgeInDays:30}")
    private Long minimumClaimAgeInDays;

    @Autowired
    private PagedResourcesAssembler<Map<String, List<UserAssignmentResponse>>> pagedResourcesAssembler;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserBucketRepository userBucketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InsuranceClaimsRepository insuranceClaimsRepository;

    @Autowired
    private PagedResourcesAssembler<UserReportingDTO> pagedResourcesAssemblerUserReporting;

    @Autowired
    private PagedResourcesAssembler<UserReporting> pagedResourcesAssemblerReporting;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private ReviewPriorityRepository reviewPriorityRepository;

    @Autowired
    private InsuranceClaimCustomRepository insuranceClaimCustomRepository;

    @Autowired
    private ClaimsValidator claimsValidator;

    public List<UserBucket> getByClaimID(String claimId) {
        return userBucketRepository.findByClaimId(UUID.fromString(claimId) , ClaimType.BucketStatus.PENDING);
    }

    public void updateUserBucketsStatus(String claimId, ExtensionStateCodeEnum stateCode, boolean auditFlag , boolean updateClaim) {
        List<UserBucket> responseBuckets = getByClaimID(claimId);
        if (!responseBuckets.isEmpty()) {
            //Updating Claim Data

            responseBuckets.forEach(bucketData -> {
                if (updateClaim) {
                    bucketData.getClaim().setApprover1(HealthIdContextHolder.userId());
                    claimsValidator.validateClaimStatusBeforeUpdate(bucketData.getClaim().getStatus() , stateCode , bucketData.getClaim().getUse() , claimId);
                    bucketData.getClaim().setStatus(stateCode);
                }
                // Updating User Bucket task completion status.
                bucketData.setStatus(ClaimType.BucketStatus.COMPLETED);
                bucketData.setActionDate(LocalDateTime.now());
                bucketData.getClaim().setAssigned(false);
            });

            userBucketRepository.saveAll(responseBuckets);
        } else {
            Optional<List<InsuranceClaim>> response = insuranceClaimsRepository.findByClaimId(UUID.fromString(claimId));
            InsuranceClaim claim = response.map(List::getFirst).orElse(null);
            assert claim != null;
            claimsValidator.validateClaimStatusBeforeUpdate(claim.getStatus() , stateCode , claim.getUse() , claimId);
            claim.setStatus(stateCode);
            if (auditFlag) {
                claim.setSurveillanceStatus(stateCode.name());
            }
            claim.setAuditFlag(auditFlag);
            claim.setApprover1(HealthIdContextHolder.userId());
            insuranceClaimsRepository.save(claim);
        }
    }

    public PagedModel<EntityModel<Map<String, List<UserAssignmentResponse>>>> getUsersGroupedByResourceNames(
            List<String> resourceNames, Pageable pageable) {
        if (resourceNames.isEmpty() || !isResourcesAllowed(listOfAllowedResources, resourceNames)) {
            throw new BadRequestException(String.format("Unsupported resource type. Allowed resource types: %s", listOfAllowedResources));
        }
        List<ResourceEnum> resourceNamesEnum = new ArrayList<>();
        resourceNamesEnum.add(ResourceEnum.PREAUTHORIZATIONS);
        // Fetch permissions for all resource names
        Optional<Page<Permission>> permissionsOpt = Optional.ofNullable(
                permissionRepository.findByResourceNameInAndStatusAndActionEdit(resourceNamesEnum, UserStatusEnum.ACTIVE, pageable)
        );

        // Fetch all user lists at once
        List<UserAssignmentResponse> usersTaskSummaryList = getUsersTaskSummary();

        // Group permissions by resource name
        Map<ResourceEnum, List<UserAssignmentResponse>> groupedByResourceName = permissionsOpt
                .filter(Page::hasContent)
                .map(permissions -> permissions.stream()
                        .collect(Collectors.groupingBy(
                                permission -> permission.getResource().getName(),
                                Collectors.mapping(
                                        permission -> getUserTaskSummaryById(usersTaskSummaryList, permission.getUser()),
                                        Collectors.toList()
                                )
                        ))
                ).orElseThrow(() -> new DataNotFoundException("No users found for the provided resource names"));

        // Convert the grouped data into a list of maps for each resource
        List<Map<String, List<UserAssignmentResponse>>> groupedResourceMaps = groupedByResourceName.entrySet().stream()
                .map(entry -> {
                    Map<String, List<UserAssignmentResponse>> resourceMap = new HashMap<>();
                    resourceMap.put(entry.getKey().toString(), entry.getValue());
                    return resourceMap;
                })
                .collect(Collectors.toList());

        // Wrap the list of maps into a Page object
        Page<Map<String, List<UserAssignmentResponse>>> groupedResourcePage = new PageImpl<>(
                groupedResourceMaps, pageable, groupedResourceMaps.size());

        // Convert to PagedModel
        return pagedResourcesAssembler.toModel(groupedResourcePage);
    }


    public Map<String, Long> processUserAssignments(UserAssignmentDTO userAssignmentDTO) {
        //validate Request
        validateRequest(userAssignmentDTO);
        //validate user permission
        List<User> permittedUsers = validateUserPermission(userAssignmentDTO);
        // Assign Task
        return assignUserTasksParallel(userAssignmentDTO, permittedUsers);
    }
    public Map<String, Long> assignUserTasksParallel(UserAssignmentDTO dto, List<User> users) {
        ClaimType claimType = ClaimType.fromCode(dto.getResourceType());
        List<InsuranceClaim> claims = insuranceClaimsRepository.findByUseAndStatusAndIsAssignedSortByCreationDate(
                        Optional.ofNullable(claimType.getDisplayName()).orElseThrow(() -> new IllegalArgumentException("Invalid Claim Type")),
                        List.of(ExtensionStateCodeEnum.CLINICAL_REVIEW), false , taskLimit)
                .orElseThrow(() -> new ResourceNotFoundException("$$No Pending Claims/PreAuth found."));

        if (claims.isEmpty()) {
            throw new ResourceNotFoundException("$$No Claims/PreAuth available for assignment.");
        }

        Map<String, Long> userTaskCount = new ConcurrentHashMap<>();
        List<UserBucket> userAssignedClaims = claims.parallelStream()
                .map(claim -> {
                    int userIndex = claims.indexOf(claim) % users.size();
                    User assignedUser = users.get(userIndex);
                    userTaskCount.merge(assignedUser.getUsername(), 1L, Long::sum);
                    claim.setAssigned(true);
                    return UserBucket.builder()
                            .claim(claim)
                            .assignedDate(LocalDateTime.now())
                            .status(ClaimType.BucketStatus.PENDING)
                            .user(assignedUser)
                            .claimType(ClaimType.fromCode(claim.getUse()))
                            .build();
                }).toList();


        userBucketRepository.saveAll(userAssignedClaims);
        log.info("Task Pending -> {} : Assigned to {} : Users", claims.size(), users.size());
        return userTaskCount;
    }

    // List of the user with task summary
    private List<UserAssignmentResponse> getUsersTaskSummary() {
        List<UserTaskSummary> userTaskSummary = userBucketRepository.findUserTaskSummary().orElseThrow(() -> new UserNotFoundException("$$Failed to fetch User Summary"));
        return UserAssignmentMapper.convertToUserAssignmentResponse(userTaskSummary);
    }

    // Get task summary for a specific user by userId
    public UserAssignmentResponse getUserTaskSummaryById(List<UserAssignmentResponse> usersTaskSummaryList, User user) {
        return usersTaskSummaryList.stream()
                .filter(userTask -> userTask.getUserId().equals(user.getId()))
                .findFirst()
                .orElseGet(() -> {
                    log.info("User with ID {} not found in task summary", user.getId());
                    return UserAssignmentResponse.builder()
                            .userId(user.getId())
                            .username(user.getUsername())
                            .taskDetails(new UserAssignmentResponse.TaskDetails(0L, 0L, 0L))
                            .build(); // return an empty UserAssignmentResponse if needed.
                });
    }


    private void validateRequest(UserAssignmentDTO userAssignmentDTO) {
        if (!listOfAllowedResources.contains(userAssignmentDTO.getResourceType())) {
            // Include listOfAllowedAssignment in the error message
            throw new BadRequestException(String.format("Unsupported resource type. Allowed resource types: %s", listOfAllowedResources));
        }
        if (userAssignmentDTO.getUsers() == null || userAssignmentDTO.getUsers().isEmpty()) {
            throw new BadRequestException("$$Invalid Request, Users can't be empty");
        }
    }

    // Method to validate if all items in ListInputAssignment are in listOfAllowedAssignment
    public static boolean isResourcesAllowed(List<String> listOfAllowedAssignment, List<String> ListInputAssignment) {
        // Check if all items in ListInput Assignment are contained in listOfAllowedAssignment
        for (String inputItem : ListInputAssignment) {
            if (!listOfAllowedAssignment.contains(inputItem)) {
                return false; // If any item is not allowed, return false
            }
        }
        return true; // All items are allowed
    }
    /**
     * To validate if passed users have valid permissions.
     * @param userAssignmentDTO Data transfer object containing user assignments.
     * @return List of users with valid permissions.
     */
    private List<User> validateUserPermission(UserAssignmentDTO userAssignmentDTO) {
        // Extract user IDs from the input DTO.
        List<Long> userIds = userAssignmentDTO.getUsers().stream()
                .map(UserAssignmentDTO.UserWithPermissions::getUserId)
                .toList();

        // Fetch users with the required permissions.
        List<User> permittedUsers = permissionRepository.findUsersByPermission(ResourceEnum.PREAUTHORIZATIONS,
                UserStatusEnum.ACTIVE, userIds).orElseThrow(() -> new UserNotFoundException("$$Users not found."));
        // List of IDs for permitted users.
        Set<Long> permittedUserIds = permittedUsers.stream().map(User::getId).collect(Collectors.toSet());

        // Find unauthorized users.
        List<String> unauthorizedUsers = userAssignmentDTO.getUsers().stream()
                .filter(user -> !permittedUserIds.contains(user.getUserId()))
                .map(UserAssignmentDTO.UserWithPermissions::getUsername)
                .toList();

        // If unauthorized users exist, throw an exception.
        if (!unauthorizedUsers.isEmpty()) {
            throw new BadRequestException("The following users do not have the required permissions: "
                    + String.join(", ", unauthorizedUsers));
        }
        // Return the list of users with valid permissions.
        return permittedUsers;
    }

    public PagedModel<EntityModel<UserReportingDTO>> getUserReporting(String resourceType, String startDate, String endDate, Pageable page) {

        // Convert start and end dates to LocalDateTime
        LocalDateTime startD = Helper.convertStringToLocalDateTime(startDate);
        LocalDateTime endD = Helper.convertStringToLocalDateTime(endDate);

        // Fetch all unique resource names
        List<ResourceEnum> resourceNamesEnum = new ArrayList<>();
        resourceNamesEnum.add(ResourceEnum.PREAUTHORIZATIONS);

        // Fetch permissions for all resource names
        Optional<Page<Permission>> permissionsOpt = Optional.ofNullable(permissionRepository.findByResourceNameInAndStatusAndActionEdit(resourceNamesEnum, UserStatusEnum.ACTIVE, page));

        // Fetch user reporting data based on the resource type and date range
        Optional<List<UserReportingDTO>> response = userBucketRepository.findUserReportingWithLatestDates(ClaimType.fromCode(resourceType), startD, endD);

        // Create a map of usernames to UserReportingDTO from the response for easy lookup
        Map<String, UserReportingDTO> responseMap = response.orElse(Collections.emptyList()).stream()
                .collect(Collectors.toMap(UserReportingDTO::getUsername, dto -> dto));

        // Use a LinkedHashMap to preserve insertion order and eliminate duplicates
        Map<String, UserReportingDTO> userReportingMap = new LinkedHashMap<>();

        // Process permissions and map to UserReportingDTO
        permissionsOpt.ifPresent(permissions -> {
            for (Permission permission : permissions.getContent()) {
                String username = permission.getUser().getUsername();
                if (!userReportingMap.containsKey(username)) {
                    UserReportingDTO userReportingDTO = responseMap.getOrDefault(username, new UserReportingDTO());
                    userReportingDTO.setUserId(permission.getUser().getId());
                    userReportingDTO.setUsername(username);
                    userReportingMap.put(username, userReportingDTO);
                }
            }
        });

        // Convert the map to a list and create a Page object
        List<UserReportingDTO> userResponse = new ArrayList<>(userReportingMap.values());
        Page<UserReportingDTO> pageResponse = new PageImpl<>(userResponse, page, userResponse.size());

        // Return the paginated result
        return pagedResourcesAssemblerUserReporting.toModel(pageResponse);
    }


    public Map<String, Long> processAssignTaskToCurrentUser(String use , ExtensionStateCodeEnum status , String code ) {

        String currentUserId = HealthIdContextHolder.userId();
        log.info("Task assignment requested by user {}", currentUserId);

        if(Objects.nonNull(code) && restrictedCodes.stream().anyMatch(code::equals)) {
            throw new UserNotAuthorizedException("Current User " + currentUserId + " not authorized for Task Assignment for Package / Sub-Package " + code);
        }

        ResourceEnum resourceName = Constants.Use.PREAUTHORIZATION.equalsIgnoreCase(use) ? ResourceEnum.PREAUTHORIZATIONS : ResourceEnum.CLAIMS;

        boolean hasPermission = permissionService.hasPermission(currentUserId, resourceName, ActionEnum.EDIT);
        if(!hasPermission) {
            throw new UserNotAuthorizedException("Current User " + currentUserId + " not authorized for Task Assignment");
        }

        // Get current user
        User currentUser = userRepository.findByUsername(currentUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + currentUserId));

        // Check existing assignments count
        long existingAssignments = userBucketRepository.countByUserIdAndStatus(
                currentUser.getId(),
                ClaimType.BucketStatus.PENDING
        );

        //TODO : Turned off this check till further discussion on this
//        if (existingAssignments > 0) {
//            throw new BadRequestException("Please complete your assigned tasks before requesting for more");
//        }

        if (existingAssignments >= taskLimit) {
            throw new BadRequestException(
                    "You have already reached the maximum allowed task limit (" + taskLimit + ")"
            );
        }

        long assignTask = taskLimit - existingAssignments;

        List<ReviewPriority> priorities = reviewPriorityRepository.findByStatusOrderByPriorityAsc(Status.ACTIVE);

        Map<ReviewPriorityEnum ,List<ReviewPriority>> reviewPriorityMap = priorities.stream().collect(Collectors.groupingBy(ReviewPriority::getCategory));

        String whereQuery = toWhereQuery(reviewPriorityMap);

        // Get unassigned claims
        List<InsuranceClaim> claims = insuranceClaimCustomRepository.findAssignedTasksClaims(
                use,
                status.name(),
                code,
                false,
                whereQuery,
                new ArrayList<>(),
                restrictedCodes,
                minimumClaimAgeInDays,
                assignTask,
                "date_from");


        List<InsuranceClaim> totalClaims = new ArrayList<>(claims);

        List<UUID> claimIds = claims.stream().map(InsuranceClaim::getClaimId).toList();

        long remaining = assignTask - totalClaims.size();

        if (remaining > 0) {
            claims = insuranceClaimCustomRepository.findAssignedTasksClaims(
                    use,
                    status.name(),
                    code,
                    false,
                    "",
                    claimIds,
                    restrictedCodes,
                    0L,
                    remaining,
                    "created_date");

            totalClaims.addAll(claims);
        }

        // HARD SAFETY CAP (ABSOLUTE GUARANTEE)
        if (totalClaims.size() > assignTask) {
            totalClaims = totalClaims.subList(0, (int) assignTask);
        }
        if (totalClaims.isEmpty()) {
            throw new ResourceNotFoundException("$$No Claims available for assignment.");
        }

        // Create assignment records
        List<UserBucket> userAssignedClaims = new ArrayList<>();

        for (InsuranceClaim claim : totalClaims) {
            // Update claim status
            claim.setAssigned(true);
            // Create UserBucket assignment
            UserBucket userBucket = UserBucket.builder()
                    .claim(claim)
                    .assignedDate(LocalDateTime.now())
                    .status(ClaimType.BucketStatus.PENDING)
                    .claimStatus(claim.getStatus())
                    .user(currentUser)
                    .claimType(ClaimType.fromCode(claim.getUse()))
                    .build();

            userAssignedClaims.add(userBucket);
        }

            // Save all changes in a transaction
            userBucketRepository.saveAll(userAssignedClaims);
            long assignedCount = totalClaims.size();

            log.info(
                    "Assigned {} tasks to user {} (limit={})",
                    assignedCount,
                    currentUser.getUsername(),
                    taskLimit
            );

        return Map.of(currentUser.getUsername(), assignedCount);
    }

    private String toWhereQuery(Map<ReviewPriorityEnum,List<ReviewPriority>> reviewPriorityMap) {
        return reviewPriorityMap.values().stream().map(this::toWhereQuery).collect(Collectors.joining("AND"));
    }

    private String toWhereQuery(List<ReviewPriority> priorities) {
        return priorities.stream().map(this::toWhereQuery).collect(Collectors.joining(" OR " , "(" , ")"));
    }

    private String toWhereQuery(ReviewPriority reviewPriority) {
        return reviewPriority.getCategory().getFunction().apply(reviewPriority);
    }

    // EPORTAL-1070
    public PagedModel<EntityModel<UserReporting>> getTodayTaskReport(String userId ,LocalDate localDate , Pageable pageable) {

        var currentUserId = HealthIdContextHolder.userId();
        log.info("Current User:{} ", currentUserId);

        boolean hasAdminPermission = permissionService.hasPermission(currentUserId, ResourceEnum.ADMIN, ActionEnum.VIEW);
        if(!hasAdminPermission) {
            throw new BadRequestException("Current User " + currentUserId + " not authorized for WorkflowReport");
        }

        Page<UserReporting> paginated = null;

        if (localDate == null) {

            paginated = userBucketRepository.getUserReporting(userId , pageable);

        }
        else {
            LocalDateTime startDate = localDate.atStartOfDay();
            LocalDateTime endDate = localDate.plusDays(1).atStartOfDay();

            paginated = userBucketRepository.getUserReporting(userId, startDate, endDate, pageable);
        }

        return pagedResourcesAssemblerReporting.toModel(paginated);
    }

    @Transactional
    public ReleaseResultDTO releaseUnactionedTasks(Long userId) {

        var currentUserId = HealthIdContextHolder.userId();
        log.info("Current User:{} ", currentUserId);

        boolean hasAdminPermission = permissionService.hasPermission(HealthIdContextHolder.userId(), ResourceEnum.ADMIN, ActionEnum.VIEW);
        if(!hasAdminPermission) {
            throw new BadRequestException("Current User " + currentUserId + " not authorized for WorkflowReport");
        }

        log.info("Starting task release for user ID: {}", userId);
        // 1. Validate user exists (quick exists check)
        if (!userRepository.existsById(userId)) {
            log.error("User not found with ID: {}", userId);
            throw new UserNotFoundException("User not found with id: " + userId);
        }

        LocalDateTime now = LocalDateTime.now();

        // 2. QUERY 2: Update related claims using subquery
        int updatedClaims = insuranceClaimsRepository.unassignClaimsFromReleasedBuckets(userId, now);

        if (updatedClaims == 0) {
            log.info("No pending tasks found for user ID: {}", userId);
            return ReleaseResultDTO.noTasks(userId);
        }

        // 3. QUERY 1: Update user buckets from PENDING to RELEASED and get count
        int releasedCount = userBucketRepository.updatePendingToReleased(userId, now , currentUserId);

        // 5. Return DTO
        return ReleaseResultDTO.success(userId, releasedCount, updatedClaims);
    }
}