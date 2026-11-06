<template>
  <el-form
    ref="studentForm"
    :model="studentBeingEdited"
    :rules="rules"
    label-width="110px"
    style="padding-right: 30px"
    label-position="left"
  >
    <el-form-item label="Id:">
      <el-input v-model="studentBeingEdited.id" disabled></el-input>
    </el-form-item>
    <el-form-item label="Username:" prop="username">
      <el-input v-model="studentBeingEdited.username"></el-input>
    </el-form-item>
    <!-- Only reactivates, until UC-STU-reactivate-student is built. Deactivating is the button below. -->
    <el-form-item v-if="loadedAsDeactivated === true" label="Status:" prop="enabled">
      <el-switch
        v-model="studentBeingEdited.enabled"
        data-cy="student-status"
        inline-prompt
        active-text="Enabled"
        inactive-text="Disabled"
        style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
      />
    </el-form-item>
    <el-form-item label="First Name:" prop="firstName">
      <el-input v-model="studentBeingEdited.firstName"></el-input>
    </el-form-item>
    <el-form-item label="Last Name:" prop="lastName">
      <el-input v-model="studentBeingEdited.lastName"></el-input>
    </el-form-item>
    <el-form-item label="Email:" prop="email">
      <el-input v-model="studentBeingEdited.email"></el-input>
    </el-form-item>
    <el-form-item label="Team:" prop="teamName">
      <el-input v-model="studentBeingEdited.teamName" disabled></el-input>
    </el-form-item>
    <div style="display: flex; justify-content: end">
      <el-button
        v-if="loadedAsDeactivated === false"
        data-cy="deactivate-student"
        type="danger"
        plain
        :loading="deactivating"
        style="margin-right: auto"
        @click="confirmDeactivation()"
      >
        Deactivate
      </el-button>
      <el-button @click="$emit('close-dialog')"> Cancel </el-button>
      <el-button type="primary" @click="updateExistingStudent()"> Confirm </el-button>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Student } from '@/apis/student/types'
import type { FormInstance } from 'element-plus'
import { deactivateStudent, findStudentById, updateStudent } from '@/apis/student'
import { h, ref, onMounted } from 'vue'

const { studentId } = defineProps(['studentId'])
const emit = defineEmits(['close-dialog'])

// Note that the structure of the userData is NOT the same as the User interface since the roles are stored as an array
const studentBeingEdited = ref<Student>({
  // Data of the user being added or edited
  id: NaN,
  username: '',
  enabled: false,
  firstName: '',
  lastName: '',
  email: ''
})

const studentForm = ref<FormInstance>() // Form reference

// Whether the student was deactivated when last loaded, unlike studentBeingEdited.enabled, which the Status switch
// changes. Null until she has loaded, so that neither the switch nor the Deactivate button flashes up first.
const loadedAsDeactivated = ref<boolean | null>(null)
const deactivating = ref(false)

// Validation rules
const rules = {
  username: [
    { required: true, message: 'Please input your email as your username', trigger: 'blur' },
    {
      pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
      message: 'Enter your email as username.',
      trigger: 'blur'
    }
  ],
  enabled: [{ required: true, message: 'Please select the status of the user.', trigger: 'blur' }],
  firstName: [
    { required: true, message: 'Please provide the first name of the user.', trigger: 'blur' }
  ],
  lastName: [
    { required: true, message: 'Please provide the last name of the user.', trigger: 'blur' }
  ],
  email: [
    { required: true, message: 'Please provide the email of the user.', trigger: 'blur' },
    { type: 'email', message: 'Please provide a valid email.', trigger: 'blur' }
  ]
}

async function loadStudent(studentId: number) {
  // Load the user to be edited
  const result = await findStudentById(studentId)

  studentBeingEdited.value.id = result.data.id as number
  studentBeingEdited.value.username = result.data.username
  studentBeingEdited.value.enabled = result.data.enabled
  loadedAsDeactivated.value = !result.data.enabled
  studentBeingEdited.value.firstName = result.data.firstName
  studentBeingEdited.value.lastName = result.data.lastName
  studentBeingEdited.value.email = result.data.email
  studentBeingEdited.value.teamName = result.data.teamName
}

onMounted(() => {
  loadStudent(studentId) // Load the artifact to be edited
})

async function updateExistingStudent() {
  await studentForm.value!.validate() // Validate the form

  const updatedStudent: Student = {
    id: studentBeingEdited.value.id,
    username: studentBeingEdited.value.username,
    // True only to reactivate her. The server reads true on a deactivated student as reactivation, so sending the true
    // this form loaded for an active student would undo a deactivation made by someone else since it opened.
    enabled: loadedAsDeactivated.value === true && studentBeingEdited.value.enabled,
    firstName: studentBeingEdited.value.firstName,
    lastName: studentBeingEdited.value.lastName,
    email: studentBeingEdited.value.email
  }
  console.log(updatedStudent)
  // Call the API to update the user
  await updateStudent(updatedStudent)

  ElMessage.success('Student updated successfully')
  emit('close-dialog')
}

// UC-STU-deactivate-student, steps 3 to 6
async function confirmDeactivation() {
  const name = `${studentBeingEdited.value.firstName} ${studentBeingEdited.value.lastName}`

  try {
    await ElMessageBox.confirm(
      h('div', { 'data-cy': 'deactivate-consequences' }, [
        h(
          'p',
          `${name} will no longer be able to log in or submit weekly activity reports and peer evaluations.`
        ),
        h(
          'p',
          'Her account and submitted work are kept, and she stays on her team, but she no longer receives reminders.'
        ),
        h(
          'p',
          "Her teammates no longer owe or may write a peer evaluation of her, and the course section's peer evaluation report lists her only for certain weeks."
        ),
        h('p', 'This is not a deletion: her account can be reactivated later.')
      ]),
      'Deactivate this student?',
      { confirmButtonText: 'Deactivate', cancelButtonText: 'Cancel', type: 'warning' }
    )
  } catch {
    return // 4a: cancelled at the warning, so nothing is sent
  }

  deactivating.value = true
  try {
    const result = await deactivateStudent(studentBeingEdited.value.id as number)
    if (result.data.noticeSent) {
      ElMessage.success(`${name} has been deactivated and notified.`)
    } else {
      ElMessage.warning(`${name} has been deactivated but could not be notified.`) // 6b
    }
    emit('close-dialog')
  } catch (error) {
    // 6a: the shared request instance has already shown the server's message
    if (axios.isAxiosError(error) && error.response?.status === 409) {
      await loadStudent(studentId)
    }
  } finally {
    deactivating.value = false
  }
}
</script>

<style scoped lang="scss"></style>
