package com.hao.blog.post.application;

import com.hao.blog.post.domain.Post;
import com.hao.blog.post.domain.PostRepository;
import com.hao.blog.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final Long AUTHOR_ID = 42L;
    private static final Long OWNER_ID = 17L;
    private static final Long OTHER_USER_ID = 99L;

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostCacheService postCacheService;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(
                postRepository,
                postCacheService
        );
    }

    @Test
    void createPost_validInput_createsPostWithAuthenticatedAuthor() {
        // Arrange
        Post savedPost = new Post(
                1L,
                "Learning Spring",
                "My first post",
                AUTHOR_ID
        );

        when(postRepository.save(any(Post.class)))
                .thenReturn(savedPost);

        // Act
        Post result = postService.createPost(
                "Learning Spring",
                "My first post",
                AUTHOR_ID
        );

        // Assert
        ArgumentCaptor<Post> captor =
                ArgumentCaptor.forClass(Post.class);

        verify(postRepository)
                .save(captor.capture());

        Post postPassedToRepository = captor.getValue();

        assertNull(postPassedToRepository.id());

        assertEquals(
                "Learning Spring",
                postPassedToRepository.title()
        );

        assertEquals(
                "My first post",
                postPassedToRepository.content()
        );

        assertEquals(
                AUTHOR_ID,
                postPassedToRepository.authorId()
        );

        assertEquals(1L, result.id());
        assertEquals("Learning Spring", result.title());
        assertEquals("My first post", result.content());
        assertEquals(AUTHOR_ID, result.authorId());

        /*
         * Creating a new post does not make an existing
         * single-post cache entry stale.
         */
        verifyNoInteractions(postCacheService);
    }

    @Test
    void getPost_existingId_returnsCachedPostView() {
        // Arrange
        PostView cachedView = new PostView(
                1L,
                "Learning Spring",
                "My first post",
                AUTHOR_ID
        );

        when(postCacheService.getPost(1L))
                .thenReturn(cachedView);

        // Act
        PostView result = postService.getPost(1L);

        // Assert
        assertEquals(1L, result.id());
        assertEquals("Learning Spring", result.title());
        assertEquals("My first post", result.content());
        assertEquals(AUTHOR_ID, result.authorId());

        verify(postCacheService)
                .getPost(1L);

        /*
         * PostService itself no longer loads the repository
         * for the GET path.
         *
         * PostCacheService owns:
         *
         * cache hit -> Redis
         * cache miss -> repository -> PostView -> Redis
         */
        verifyNoInteractions(postRepository);
    }

    @Test
    void getPost_missingId_propagatesPostNotFoundException() {
        // Arrange
        when(postCacheService.getPost(999L))
                .thenThrow(new PostNotFoundException(999L));

        // Act + Assert
        assertThrows(
                PostNotFoundException.class,
                () -> postService.getPost(999L)
        );

        verify(postCacheService)
                .getPost(999L);

        verifyNoInteractions(postRepository);
    }

    @Test
    void updatePost_ownerUser_returnsUpdatedPostAndEvictsCache() {
        // Arrange
        Post existingPost = new Post(
                1L,
                "Original",
                "Original content",
                OWNER_ID
        );

        Post updatedPost = new Post(
                1L,
                "Changed",
                "New content",
                OWNER_ID
        );

        when(postRepository.findById(1L))
                .thenReturn(Optional.of(existingPost));

        when(postRepository.update(
                1L,
                "Changed",
                "New content"
        )).thenReturn(Optional.of(updatedPost));

        // Act
        Post result = postService.updatePost(
                1L,
                "Changed",
                "New content",
                OWNER_ID,
                UserRole.USER
        );

        // Assert
        assertEquals(1L, result.id());
        assertEquals("Changed", result.title());
        assertEquals("New content", result.content());
        assertEquals(OWNER_ID, result.authorId());

        verify(postRepository)
                .findById(1L);

        verify(postRepository)
                .update(
                        1L,
                        "Changed",
                        "New content"
                );

        /*
         * The authoritative DB write succeeded,
         * therefore the stale cached read model must
         * be invalidated.
         */
        verify(postCacheService)
                .evictPost(1L);
    }

    @Test
    void updatePost_missingPost_throwsPostNotFoundExceptionAndDoesNotEvictCache() {
        // Arrange
        when(postRepository.findById(999L))
                .thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                PostNotFoundException.class,
                () -> postService.updatePost(
                        999L,
                        "Changed",
                        "New content",
                        AUTHOR_ID,
                        UserRole.USER
                )
        );

        verify(postRepository)
                .findById(999L);

        verify(postRepository, never())
                .update(
                        anyLong(),
                        anyString(),
                        anyString()
                );

        /*
         * Nothing changed in PostgreSQL,
         * therefore there is nothing stale to evict.
         */
        verify(postCacheService, never())
                .evictPost(anyLong());
    }

    @Test
    void updatePost_nonOwnerUser_throwsPostAccessDeniedExceptionAndDoesNotEvictCache() {
        // Arrange
        Post existingPost = new Post(
                1L,
                "Original",
                "Content",
                OWNER_ID
        );

        when(postRepository.findById(1L))
                .thenReturn(Optional.of(existingPost));

        // Act + Assert
        assertThrows(
                PostAccessDeniedException.class,
                () -> postService.updatePost(
                        1L,
                        "Changed",
                        "New content",
                        OTHER_USER_ID,
                        UserRole.USER
                )
        );

        verify(postRepository)
                .findById(1L);

        verify(postRepository, never())
                .update(
                        anyLong(),
                        anyString(),
                        anyString()
                );

        /*
         * Authorization failed before mutation,
         * so the existing cache remains valid.
         */
        verify(postCacheService, never())
                .evictPost(anyLong());
    }

    @Test
    void updatePost_nonOwnerAdmin_updatesPostAndEvictsCache() {
        // Arrange
        Post existingPost = new Post(
                1L,
                "Original title",
                "Original content",
                OWNER_ID
        );

        Post updatedPost = new Post(
                1L,
                "Admin changed title",
                "Admin changed content",
                OWNER_ID
        );

        when(postRepository.findById(1L))
                .thenReturn(Optional.of(existingPost));

        when(postRepository.update(
                1L,
                "Admin changed title",
                "Admin changed content"
        )).thenReturn(Optional.of(updatedPost));

        // Act
        Post result = postService.updatePost(
                1L,
                "Admin changed title",
                "Admin changed content",
                OTHER_USER_ID,
                UserRole.ADMIN
        );

        // Assert
        assertEquals(
                "Admin changed title",
                result.title()
        );

        assertEquals(
                "Admin changed content",
                result.content()
        );

        /*
         * Admin authorization overrides the ownership
         * restriction, but does NOT transfer ownership.
         */
        assertEquals(
                OWNER_ID,
                result.authorId()
        );

        verify(postRepository)
                .findById(1L);

        verify(postRepository)
                .update(
                        1L,
                        "Admin changed title",
                        "Admin changed content"
                );

        verify(postCacheService)
                .evictPost(1L);
    }

    @Test
    void updatePost_repositoryUpdateReturnsEmpty_throwsPostNotFoundExceptionAndDoesNotEvictCache() {
        // Arrange
        Post existingPost = new Post(
                1L,
                "Original",
                "Original content",
                OWNER_ID
        );

        when(postRepository.findById(1L))
                .thenReturn(Optional.of(existingPost));

        /*
         * Simulates the row disappearing between
         * authorization/load and the actual update.
         */
        when(postRepository.update(
                1L,
                "Changed",
                "New content"
        )).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                PostNotFoundException.class,
                () -> postService.updatePost(
                        1L,
                        "Changed",
                        "New content",
                        OWNER_ID,
                        UserRole.USER
                )
        );

        verify(postRepository)
                .findById(1L);

        verify(postRepository)
                .update(
                        1L,
                        "Changed",
                        "New content"
                );

        /*
         * updatePost() did not successfully produce
         * the updated authoritative state, so eviction
         * must not be requested.
         */
        verify(postCacheService, never())
                .evictPost(anyLong());
    }
}