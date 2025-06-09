package piper1970.eventservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import piper1970.eventservice.common.events.dto.EventDto;
import piper1970.eventservice.common.tokens.TokenUtilities;
import piper1970.eventservice.dto.model.EventCreateRequest;
import piper1970.eventservice.dto.model.EventUpdateRequest;
import piper1970.eventservice.service.EventWebService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/events")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Event Controller")
public class EventController {

  private final EventWebService eventWebService;

  @Operation(
      summary = "Get all available events",
      responses = {
          @ApiResponse(
              responseCode = "200",
              description = "found all events",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      array = @ArraySchema(schema = @Schema(implementation = EventDto.class))
                  )
              }),
          @ApiResponse(
              responseCode = "401",
              description = "Unauthorized. Click the Authorize button to use OAuth2 authorization",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "403",
              description = "Forbidden. This section is not accessible with your current role",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          )
      }
  )
  @GetMapping
  @PreAuthorize("hasAuthority('MEMBER')")
  public Flux<EventDto> getEvents(@AuthenticationPrincipal JwtAuthenticationToken jwtToken) {

    if(log.isDebugEnabled()) {
      var user = TokenUtilities.getUserFromToken(jwtToken);
      log.debug("User [{}] is retrieving all events", user);
    }

    return eventWebService.getEvents();
  }

  @Operation(
      summary = "Get event by id",
      responses = {
          @ApiResponse(
              responseCode = "200",
              description = "found event",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = EventDto.class)
                  )
              }),
          @ApiResponse(
              responseCode = "404",
              description = "event not found for given id",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "401",
              description = "Unauthorized. Click the Authorize button to use OAuth2 authorization",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "403",
              description = "Forbidden. This section is not accessible with your current role",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          )
      }
  )
  @GetMapping("{id}")
  @PreAuthorize("hasAuthority('MEMBER')")
  public Mono<EventDto> getEvent(@AuthenticationPrincipal JwtAuthenticationToken jwtToken,
      @Parameter(description = "id of event to retrieve") @PathVariable Integer id) {

    if(log.isDebugEnabled()) {
      var user = TokenUtilities.getUserFromToken(jwtToken);
      log.debug("User [{}] is retrieving event [{}]", user, id);
    }

    return eventWebService.getEvent(id);
  }

  @Operation(
      summary = "Create new booking for given event id",
      responses = {
          @ApiResponse(
              responseCode = "201",
              description = "event created for user",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = EventDto.class)
                  )
              }),
          @ApiResponse(
              responseCode = "401",
              description = "Unauthorized. Click the Authorize button to use OAuth2 authorization",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "403",
              description = "Forbidden. This section is not accessible with your current role",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          )
      }
  )
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERFORMER')")
  public Mono<EventDto> createEvent(@AuthenticationPrincipal JwtAuthenticationToken jwtToken,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "json create-event request",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = EventCreateRequest.class),
              examples = @ExampleObject(value = """
                  {
                    "title": "My awesome new event",
                    "description": "Let the good times roll. Music and dancing for everyone",
                    "location": "At the neighborhood hangout",
                    "eventDateTime": "2026-05-02T15:30",
                    "durationInMinutes": 90,
                    "availableBookings": 30
                  }
                  """
              )
          )
      )@Valid @RequestBody EventCreateRequest createRequest) {

    var user = TokenUtilities.getUserFromToken(jwtToken);
    createRequest.setFacilitator(user);
    log.debug("Facilitator [{}] is creating event", user);

    return eventWebService.createEvent(createRequest);
  }

  @Operation(
      summary = "update event",
      responses = {
          @ApiResponse(
              responseCode = "200",
              description = "event has been successfully updated",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = EventDto.class)
                  )
              }
          ),
          @ApiResponse(
              responseCode = "404",
              description = "event not found for given id",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "400",
              description = "Cannot update event once event starts",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "401",
              description = "Unauthorized. Click the Authorize button to use OAuth2 authorization",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "403",
              description = "Forbidden. This section is not accessible with your current role",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          )
      }
  )
  @PutMapping("{id}")
  @PreAuthorize("hasAuthority('PERFORMER')")
  public Mono<EventDto> updateEvent(@AuthenticationPrincipal JwtAuthenticationToken jwtToken,
      @Parameter(description = "id of event to update") @PathVariable Integer id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "json update-event request",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = EventUpdateRequest.class),
              examples = @ExampleObject(value = """
                  {
                    "title": "Some update (or null)" ,
                    "description": "Some update (or null)",
                    "location": "Some update (or null)",
                    "eventDateTime": "2026-05-02T15:30 (or null)",
                    "durationInMinutes": 90 (or null),
                    "availableBookings": 30 (or null)
                  }
                  """
              )
          )
      ) @Valid @RequestBody EventUpdateRequest updateRequest) {

    var facilitator = TokenUtilities.getUserFromToken(jwtToken);
    log.debug("Facilitator [{}] is updating event [{}]", facilitator, id);

    return eventWebService.updateEvent(id, facilitator, updateRequest);
  }

  @Operation(
      summary = "cancel event",
      responses = {
          @ApiResponse(
              responseCode = "200",
              description = "event has been successfully cancelled",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = EventDto.class)
                  )
              }
          ),
          @ApiResponse(
              responseCode = "404",
              description = "event created by user not found for given id",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "400",
              description = "cannot cancel event that is in progress or has completed",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "401",
              description = "Unauthorized. Click the Authorize button to use OAuth2 authorization",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          ),
          @ApiResponse(
              responseCode = "403",
              description = "Forbidden. This section is not accessible with your current role",
              content = {
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE
                  )
              }
          )
      }
  )
  @PatchMapping("{id}/cancel")
  @PreAuthorize("hasAuthority('PERFORMER')")
  public Mono<EventDto> cancelEvent(@AuthenticationPrincipal JwtAuthenticationToken jwtToken,
      @Parameter(description = "id of event to cancel") @PathVariable Integer id){

    var facilitator = TokenUtilities.getUserFromToken(jwtToken);
    log.debug("Cancel event[{}] called by [{}]", id, facilitator);

    return eventWebService.cancelEvent(id, facilitator);
  }
}
