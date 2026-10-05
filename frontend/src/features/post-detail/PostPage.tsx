import { useParams } from 'react-router-dom'
import { PostDetail } from './PostDetail'
import styles from './PostPage.module.css'

export function PostPage() {
  const { postId } = useParams<{ postId: string }>()
  if (!postId) return null

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <PostDetail postId={Number(postId)} />
      </div>
    </div>
  )
}
