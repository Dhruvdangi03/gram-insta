import { useNavigate, useParams } from 'react-router-dom'
import { Modal } from '@/components/Modal'
import { PostDetail } from './PostDetail'
import styles from './PostDetailModal.module.css'

export function PostDetailModal() {
  const { postId } = useParams<{ postId: string }>()
  const navigate = useNavigate()

  if (!postId) return null

  return (
    <Modal onClose={() => navigate(-1)} contentClassName={styles.content}>
      <PostDetail postId={Number(postId)} />
    </Modal>
  )
}
