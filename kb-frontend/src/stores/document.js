import { defineStore } from 'pinia';
import * as documentApi from '../api/document';

const toPageData = (data) => {
  if (Array.isArray(data)) {
    return {
      list: data,
      total: data.length
    };
  }

  const list = data?.records || data?.list || data?.items || [];

  return {
    list,
    total: data?.total ?? list.length
  };
};

export const useDocumentStore = defineStore('document', {
  state: () => ({
    docList: [],
    total: 0,
    // 用 Map 而不是 Array 存储上传任务：Map 根据 taskId 查找是 O(1)，Array 需要遍历。
    uploadingTasks: new Map()
  }),

  actions: {
    async loadDocList(params) {
      const data = await documentApi.getDocList(params);
      const pageData = toPageData(data);

      this.docList = pageData.list;
      this.total = pageData.total;

      return pageData;
    },

    startUploadTask(docId, taskId) {
      // 上传接口返回 taskId 后调用此方法开始追踪该文档处理任务。
      this.uploadingTasks.set(taskId, {
        docId,
        progress: 0,
        status: 'PENDING'
      });
    },

    updateTaskProgress(taskId, status, progress) {
      const task = this.uploadingTasks.get(taskId);

      if (!task) {
        return;
      }

      this.uploadingTasks.set(taskId, {
        ...task,
        status,
        progress
      });
    },

    removeTask(taskId) {
      this.uploadingTasks.delete(taskId);
    }
  }
});
