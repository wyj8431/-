import baiduIcon from '@/assets/auth-icons/reference/baidu.png'
import dingtalkIcon from '@/assets/auth-icons/reference/dingtalk.png'
import phoneIcon from '@/assets/auth-icons/reference/phone.png'
import qqIcon from '@/assets/auth-icons/reference/qq.png'
import wechatIcon from '@/assets/auth-icons/reference/wechat.png'
import weiboIcon from '@/assets/auth-icons/reference/weibo.png'

export const authProviderIcons = [
  { key: 'phone', label: '手机号登录', src: phoneIcon },
  { key: 'qq', label: 'QQ登录', src: qqIcon },
  { key: 'weibo', label: '微博登录', src: weiboIcon },
  { key: 'wechat', label: '微信登录', src: wechatIcon },
  { key: 'dingtalk', label: '钉钉登录', src: dingtalkIcon },
  { key: 'baidu', label: '百度登录', src: baiduIcon },
] as const
